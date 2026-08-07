/**
 * 来电/呼出铃声（Web Audio 合成，避免引入音频资源文件）。
 * 每 1s 循环一段短音；静音开关由调用方控制。
 */
let ctx: AudioContext | null = null
let timer: number | null = null

function beep() {
  if (!ctx) return
  const osc = ctx.createOscillator()
  const gain = ctx.createGain()
  osc.type = 'sine'
  osc.frequency.value = 440
  gain.gain.setValueAtTime(0.06, ctx.currentTime)
  osc.connect(gain)
  gain.connect(ctx.destination)
  osc.start()
  osc.stop(ctx.currentTime + 0.4)
}

export function startRing(): void {
  stopRing()
  try {
    ctx = new AudioContext()
    beep()
    timer = window.setInterval(beep, 1000)
  } catch {
    // 无音频上下文（浏览器限制）时静默
  }
}

export function stopRing(): void {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
  if (ctx) {
    ctx.close().catch(() => {})
    ctx = null
  }
}
