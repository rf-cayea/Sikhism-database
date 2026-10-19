#!/usr/bin/env bun
/**
 * Checks, on an attached Android 16+ device, that the reader comes back as it was
 * after each thing that recreates the activity while the process lives: rotation
 * both ways, a dark theme toggle, a Font size change, and "Don't keep activities".
 * Then that **the process being killed in the background starts a new journey**: the
 * default screen, offering `continue reading` for the bani just read, since it is a
 * daily bani read under 8 hours ago (decided 2026-10-16), and the reader's saved
 * settings still there when it is opened again from there. The README's hand
 * checklist, as one command (apps/android/README.md#reader-state-across-recreation).
 *
 *   bun apps/android/scripts/check-restore.ts ["ਬੇਨਤੀ ਚੌਪਈ ਸਾਹਿਬ"]
 *
 * Install the build first (`./apps/build.sh android --run`). With more than one
 * device attached, set ANDROID_SERIAL. Run it when a change touches the reader's
 * state, the activity, or what the first composition does — not on every commit.
 *
 * **Only adb and the device's own tools**, so it adds no dependency: `monkey` sends
 * the launcher's intent, `uiautomator dump` reads the screen's accessibility tree,
 * `settings` and `cmd uimode` change the system. It drives the real app, so it
 * reads the screen by what the person sees — row text, "Journeys", "Controls",
 * "Expand", "Pauses", "Latin", the greeting — and must follow the app when those
 * change. Banis are opened from the Journeys sidebar, which lists them in Gurmukhi.
 *
 * **What it checks, after every step but the kill:** the same bani is open; the same row is at
 * the top; the expanded continuation is still expanded. The last is checked
 * through the top row: the script puts the continuation's first line at the top,
 * and that row only exists while it is expanded, so a collapsed continuation shows
 * as a different top row. Latin is in the top row's text too, so a lost Latin
 * setting shows there as well.
 *
 * **Pauses and Latin are read from the Controls menu after the process kill
 * only**, once the bani is opened again. They come back from saved preferences, not
 * from the journey, and a killed process is the hardest case for that; reading the
 * menu after every step opened it nine more times to learn nothing new.
 *
 * **What it does not check:** how quickly the screen rotates (watch
 * `adb logcat -s Choreographer:I`), or the 15-minute idle boundary (ShellTest).
 *
 * Every system setting it changes, and the reader settings it toggles, are put back
 * before it exits, pass or fail.
 */
import { execFileSync } from 'node:child_process'
import { existsSync } from 'node:fs'
import { homedir } from 'node:os'
import { join } from 'node:path'

const APP = 'com.shabados.app'
// A bani with a continuation, so the expanded state is exercised. Benti Chaupai
// has one. Its Gurmukhi name, as the sidebar shows it (app/src/main/assets/banis.json).
const BANI = process.argv[2] ?? 'ਬੇਨਤੀ ਚੌਪਈ ਸਾਹਿਬ'
const GREETING = 'ਸਤਿ ਸ੍ਰੀ ਅਕਾਲ'
// How long to keep re-reading the screen for an expected state before failing.
// Waits are on what the screen shows, not fixed sleeps, except where a change has
// nothing on screen to wait for (see SETTLE_MS).
const TIMEOUT_MS = 10_000
// A theme or Font size change shows nothing in the accessibility tree, so this is
// how long the activity gets to be recreated before the screen is read.
const SETTLE_MS = 1500

// ------------------------------------------------------------------ adb

// The same lookup as apps/build.sh's android_sdk.
const sdk = process.env.ANDROID_HOME ?? process.env.ANDROID_SDK_ROOT ?? join(homedir(), 'Library/Android/sdk')
const adbPath = join(sdk, 'platform-tools/adb')
if (!existsSync(adbPath)) {
  console.error(`✗ adb not found at ${adbPath}`)
  process.exit(1)
}

function adb(...args: string[]): string {
  return execFileSync(adbPath, args, { encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'] }).trim()
}
const shell = (command: string) => adb('shell', command)
const sleep = (ms: number) => Bun.sleepSync(ms)

/** Throws rather than exiting, so the `finally` below still puts the device's
 * settings back. */
function fail(message: string): never {
  throw new Error(message)
}

// ------------------------------------------------------------------ screen

type Node = {
  /** The node's own text. */
  text: string
  /** For a clickable node, the text of everything inside it that is not itself
   * clickable — a row's lines. Compose puts the text on child nodes, not on the
   * clickable row (seen on a phone 2026-10-15), so a row is matched by this. */
  content: string
  /** Whether any ancestor is clickable: a row's lines are, the toolbar title is not. */
  inClickable: boolean
  desc: string
  /** The app the node belongs to: after Home, the launcher is on screen instead. */
  pkg: string
  clickable: boolean
  checkable: boolean
  checked: boolean
  top: number
  bottom: number
  x: number
  y: number
}

const unescape = (s: string) =>
  s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&apos;/g, "'").replace(/&#10;/g, '\n').replace(/&amp;/g, '&')

/** The display's rotation, in quarter turns, as of the last [screen] read. */
let shownRotation = -1

/** The screen's accessibility tree. Retried, because uiautomator refuses to dump
 * while the screen is still animating. */
function screen(): Node[] {
  for (let attempt = 1; ; attempt++) {
    try {
      shell('uiautomator dump /sdcard/check-restore.xml')
      const xml = adb('exec-out', 'cat', '/sdcard/check-restore.xml')
      shownRotation = Number(xml.match(/<hierarchy rotation="(\d)"/)?.[1] ?? -1)
      const nodes: Node[] = []
      // Open nodes, innermost last, so each text can be given to the clickable
      // node around it.
      const open: Node[] = []
      for (const [tag, attrs, selfClosing] of xml.matchAll(/<node ([^>]*?)(\/?)>|<\/node>/g)) {
        if (tag === '</node>') {
          open.pop()
          continue
        }
        const attr = (name: string) => unescape(attrs.match(new RegExp(`\\b${name}="([^"]*)"`))?.[1] ?? '')
        const [x1, y1, x2, y2] = (attr('bounds').match(/-?\d+/g) ?? []).map(Number)
        const node: Node = {
          text: attr('text'),
          content: '',
          inClickable: open.some((n) => n.clickable),
          desc: attr('content-desc'),
          pkg: attr('package'),
          clickable: attr('clickable') === 'true',
          checkable: attr('checkable') === 'true',
          checked: attr('checked') === 'true',
          top: y1,
          bottom: y2,
          x: Math.round((x1 + x2) / 2),
          y: Math.round((y1 + y2) / 2),
        }
        nodes.push(node)
        const owner = node.clickable ? node : open.findLast((n) => n.clickable)
        if (owner && node.text) owner.content = owner.content ? `${owner.content}\n${node.text}` : node.text
        if (!selfClosing) open.push(node)
      }
      return nodes
    } catch (error) {
      if (attempt >= 5) throw error
      sleep(1000)
    }
  }
}

/** Re-reads the screen until [ready] holds, and returns what it showed. */
function waitFor(what: string, ready: (nodes: Node[]) => boolean): Node[] {
  const deadline = Date.now() + TIMEOUT_MS
  for (;;) {
    const nodes = screen()
    if (ready(nodes)) return nodes
    if (Date.now() > deadline) fail(`timed out waiting for ${what}`)
    sleep(250)
  }
}

const appShown = (nodes: Node[]) => nodes.some((n) => n.pkg === APP)
const readerShown = (nodes: Node[]) => headerButton(nodes) !== undefined && rows(nodes).length > 0
const defaultShown = (nodes: Node[]) => nodes.some((n) => n.pkg === APP && n.text === GREETING)

const tap = (node: Node) => shell(`input tap ${node.x} ${node.y}`)
const back = () => shell('input keyevent KEYCODE_BACK')
function home() {
  shell('input keyevent KEYCODE_HOME')
  waitFor('the home screen', (nodes) => !appShown(nodes))
}

/** The launcher's own intent, never `am start -n`: a task started with `-n` does not
 * match the icon's intent, and the icon then starts a fresh activity
 * (apps/android/README.md#reader-state-across-recreation). */
function launch(ready: (nodes: Node[]) => boolean = readerShown) {
  shell(`monkey -p ${APP} -c android.intent.category.LAUNCHER 1`)
  waitFor('Shabad OS to open', ready)
}

/** Fast flings: each carries the list several screens. For reaching the end of a
 * bani, where Benti Chaupai's continuation is. */
function flingUp(nodes: Node[]) {
  const height = Math.max(...nodes.map((n) => n.bottom))
  for (let i = 0; i < 3; i++) shell(`input swipe 500 ${Math.round(height * 0.85)} 500 ${Math.round(height * 0.15)} 60`)
  sleep(700)
}

// ------------------------------------------------------------------ the reader

type Reader = { title: string; topRow: string; pauses?: boolean; latin?: boolean }

/** The reader's Controls button: present only while a bani is open, and it marks
 * the header's band. */
function headerButton(nodes: Node[]): Node | undefined {
  return nodes.find((n) => n.desc === 'Controls')
}

/** Reader rows: clickable, with text, below the header's buttons. The Expand
 * button is clickable text too, and is not a row. The sidebar's rows are not in the
 * tree while it is closed. */
function rows(nodes: Node[]): Node[] {
  const below = headerButton(nodes)?.bottom ?? 0
  return nodes
    .filter((n) => n.clickable && n.content !== '' && n.content !== 'Expand' && n.top >= below)
    .sort((a, b) => a.top - b.top)
}

function find(nodes: Node[], match: (n: Node) => boolean, what: string): Node {
  return nodes.find(match) ?? fail(`could not find ${what} on screen`)
}

/** Whether a Controls menu item is checked: its own state, or the checkbox on its
 * row. */
function checked(nodes: Node[], label: string): boolean {
  const item = find(nodes, (n) => n.text === label, `"${label}" in the Controls menu`)
  if (item.checkable) return item.checked
  const box = nodes.find((n) => n.checkable && n.y >= item.top && n.y <= item.bottom)
  return box?.checked ?? fail(`could not find the checkbox beside "${label}"`)
}

const menuShown = (nodes: Node[]) => nodes.some((n) => n.text === 'Pauses')

function withMenu<T>(use: (nodes: Node[]) => T): T {
  tap(find(screen(), (n) => n.desc === 'Controls', 'the Controls button'))
  const menu = waitFor('the Controls menu', menuShown)
  try {
    return use(menu)
  } finally {
    // Closes the menu only. Pressed with no menu open, it would leave the app.
    back()
    waitFor('the Controls menu to close', (nodes) => !menuShown(nodes))
  }
}

function setSetting(label: string, on: boolean) {
  withMenu((nodes) => {
    if (checked(nodes, label) === on) return
    tap(find(nodes, (n) => n.text === label, `"${label}"`))
    waitFor(`"${label}" to change`, (after) => menuShown(after) && checked(after, label) === on)
  })
}

/** Opens [BANI] from the Journeys sidebar, the way a person does. */
function openFromSidebar() {
  tap(find(screen(), (n) => n.desc === 'Journeys', 'the Journeys button'))
  const row = (nodes: Node[]) => nodes.find((n) => n.clickable && n.content === BANI)
  const nodes = waitFor(`"${BANI}" in the Journeys sidebar`, (nodes) => row(nodes) !== undefined)
  tap(row(nodes)!)
  waitFor(`"${BANI}" to open`, readerShown)
}

/** What the reader shows. With [settings], also Pauses and Latin, from the menu. */
function read(settings: boolean): Reader {
  const nodes = screen()
  const header =
    headerButton(nodes) ??
    fail(defaultShown(nodes) ? 'the reader is not open — the default screen is showing' : 'the reader is not open (no Controls button)')
  // In the header's band and not inside a row: rows scroll under the header too.
  const title =
    nodes.find((n) => n.text !== '' && !n.inClickable && n.y >= header.top && n.y <= header.bottom)?.text ?? ''
  const topRow = rows(nodes)[0]?.content ?? fail('no reader rows on screen')
  if (!settings) return { title, topRow }
  const [pauses, latin] = withMenu((menu) => [checked(menu, 'Pauses'), checked(menu, 'Latin')])
  return { title, topRow, pauses, latin }
}

// ------------------------------------------------------------------ system settings

const original = {
  accelerometer: shell('settings get system accelerometer_rotation'),
  rotation: shell('settings get system user_rotation'),
  fontScale: shell('settings get system font_scale'),
  night: /yes/.test(shell('cmd uimode night')),
  finishActivities: shell('settings get global always_finish_activities'),
  stayOn: shell('settings get global stay_on_while_plugged_in'),
}

function put(namespace: string, key: string, value: string) {
  shell(value === 'null' ? `settings delete ${namespace} ${key}` : `settings put ${namespace} ${key} ${value}`)
}

/** Waits for the display to turn and, when [reader] is set, for the recreated
 * reader to be on screen. */
function rotate(quarterTurns: 0 | 1, reader = true) {
  put('system', 'accelerometer_rotation', '0')
  put('system', 'user_rotation', String(quarterTurns))
  waitFor(`rotation ${quarterTurns}`, (nodes) => shownRotation === quarterTurns && (!reader || readerShown(nodes)))
  // The display can report the new rotation while the old activity's rows are
  // still on screen; a moment lets the recreated one replace them.
  if (reader) sleep(500)
}

/** For a change with nothing on screen to wait for: a fixed pause, then the reader. */
function settle() {
  sleep(SETTLE_MS)
  waitFor('the reader', readerShown)
}

function restoreSystem() {
  put('system', 'accelerometer_rotation', original.accelerometer)
  put('system', 'user_rotation', original.rotation)
  put('system', 'font_scale', original.fontScale)
  put('global', 'always_finish_activities', original.finishActivities)
  put('global', 'stay_on_while_plugged_in', original.stayOn)
  shell(`cmd uimode night ${original.night ? 'yes' : 'no'}`)
}

// ------------------------------------------------------------------ run

const sdkLevel = Number(shell('getprop ro.build.version.sdk'))
if (sdkLevel < 36) {
  console.error(`✗ the attached device is API ${sdkLevel}; the app needs 36. Set ANDROID_SERIAL to pick another.`)
  process.exit(1)
}
try {
  shell(`pm path ${APP}`)
} catch {
  console.error(`✗ ${APP} is not installed. Run ./apps/build.sh android first.`)
  process.exit(1)
}

let savedReaderSettings: { pauses: boolean; latin: boolean } | undefined
const results: { step: string; ok: boolean; detail: string }[] = []
let setupError: unknown

try {
  // The screen stays on while the device is plugged in (7: AC, USB and wireless),
  // for this run only. Several steps wait without touching the screen, and a
  // short screen timeout put a phone to sleep partway through (2026-10-16).
  put('global', 'stay_on_while_plugged_in', '7')
  shell('input keyevent KEYCODE_WAKEUP')

  // A fresh start, opened the way a person opens it.
  shell(`am force-stop ${APP}`)
  rotate(0, false)
  // A fresh process opens to the default screen.
  launch(defaultShown)
  openFromSidebar()

  let nodes = screen()
  // Settings away from their defaults, so a reset shows. Put back at the end.
  savedReaderSettings = withMenu((menu) => ({ pauses: checked(menu, 'Pauses'), latin: checked(menu, 'Latin') }))
  setSetting('Pauses', false)
  setSetting('Latin', true)

  // Expand the continuation, then put its first line at the top.
  nodes = screen()
  for (let i = 0; i < 20 && !nodes.some((n) => n.text === 'Expand'); i++) {
    flingUp(nodes)
    nodes = screen()
  }
  const expand = nodes.find((n) => n.text === 'Expand')
  if (expand) {
    const card = rows(nodes).filter((n) => n.top <= expand.top).at(-1) ?? expand
    tap(expand)
    const firstLine = rows(waitFor('the continuation to expand', (after) => !after.some((n) => n.text === 'Expand'))).reduce((best, n) => (Math.abs(n.top - card.top) < Math.abs(best.top - card.top) ? n : best))
    tap(firstLine)
  } else {
    console.warn(`! no "Expand" in "${BANI}" — the expanded state is not being checked`)
    const visible = rows(nodes)
    tap(visible[Math.floor(visible.length / 2)])
  }
  // The tapped row scrolls to the top; give the scroll time to finish.
  sleep(1000)

  const expected = read(true)
  console.log(`Reader: ${expected.title}`)
  console.log(`Top row: ${expected.topRow.slice(0, 60)}…`)

  // [step, what it does, whether to read Pauses and Latin afterwards]
  const steps: [string, () => void, boolean?][] = [
    ['rotate to landscape', () => rotate(1)],
    ['rotate to portrait', () => rotate(0)],
    ['rotate to landscape again', () => rotate(1)],
    ['rotate to portrait again', () => rotate(0)],
    ['dark theme toggled', () => { shell(`cmd uimode night ${original.night ? 'no' : 'yes'}`); settle() }],
    ['dark theme toggled back', () => { shell(`cmd uimode night ${original.night ? 'yes' : 'no'}`); settle() }],
    ['Font size 1.3', () => { put('system', 'font_scale', '1.3'); settle() }],
    ['Font size back', () => { put('system', 'font_scale', original.fontScale); settle() }],
    ["Don't keep activities, Home, icon", () => {
      put('global', 'always_finish_activities', '1')
      home()
      launch()
      put('global', 'always_finish_activities', original.finishActivities)
    }],
  ]

  for (const [step, run, settings = false] of steps) {
    run()
    let actual: Reader
    try {
      actual = read(settings)
    } catch (error) {
      const detail = error instanceof Error ? error.message : String(error)
      results.push({ step, ok: false, detail })
      console.log(`✗ ${step} — ${detail}`)
      continue
    }
    const differences = (Object.keys(actual) as (keyof Reader)[])
      .filter((key) => actual[key] !== expected[key])
      .map((key) => `${key}: expected ${JSON.stringify(expected[key])}, got ${JSON.stringify(actual[key])}`)
    results.push({ step, ok: differences.length === 0, detail: differences.join('; ') })
    console.log(`${differences.length === 0 ? '✓' : '✗'} ${step}${differences.length ? ` — ${differences.join('; ')}` : ''}`)
  }

  // Process death is a new journey: the default screen, offering to continue the
  // bani just read. The settings are saved preferences, so they are still there once
  // it is open again.
  home()
  shell(`am kill ${APP}`)
  sleep(1000)
  if (shell(`pidof ${APP} || true`)) fail('the process survived `am kill`; the step would not test process death')
  launch(defaultShown)
  const continueButton = screen().find((n) => n.clickable && n.content.startsWith('Continue reading'))
  const continueOk = continueButton?.content.includes(expected.title) ?? false
  const killStep = `process killed in the background: default screen, continue reading "${expected.title}"`
  results.push({ step: killStep, ok: continueOk, detail: continueButton ? continueButton.content : 'no "Continue reading"' })
  console.log(`${continueOk ? '✓' : '✗'} ${killStep}${continueOk ? '' : ` — ${continueButton?.content ?? 'no "Continue reading"'}`}`)
  if (!continueButton) fail('nothing to continue, so the bani cannot be reopened the way a person would')
  tap(continueButton)
  waitFor(`"${BANI}" to reopen`, readerShown)
  const reopened = read(true)
  const settingsStep = 'after the kill, the bani reopened keeps Pauses and Latin'
  const settingsOk = reopened.pauses === expected.pauses && reopened.latin === expected.latin
  results.push({ step: settingsStep, ok: settingsOk, detail: `pauses ${reopened.pauses}, latin ${reopened.latin}` })
  console.log(`${settingsOk ? '✓' : '✗'} ${settingsStep}${settingsOk ? '' : ` — pauses ${reopened.pauses}, latin ${reopened.latin}`}`)
} catch (error) {
  setupError = error
} finally {
  restoreSystem()
  if (savedReaderSettings && headerButtonVisible()) {
    try {
      setSetting('Pauses', savedReaderSettings.pauses)
      setSetting('Latin', savedReaderSettings.latin)
      savedReaderSettings = undefined
    } catch {
      // Reported below.
    }
  }
}

function headerButtonVisible(): boolean {
  try {
    return headerButton(screen()) !== undefined
  } catch {
    return false
  }
}

if (savedReaderSettings) {
  console.warn('! Pauses and Latin could not be put back; set them by hand')
}
if (setupError) {
  console.error(`\n✗ stopped before every step ran: ${setupError instanceof Error ? setupError.message : String(setupError)}`)
  process.exit(1)
}
const failed = results.filter((r) => !r.ok)
console.log(`\n${results.length - failed.length}/${results.length} steps passed.`)
process.exit(failed.length === 0 ? 0 : 1)
