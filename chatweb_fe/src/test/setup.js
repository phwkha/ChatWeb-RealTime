import '@testing-library/jest-dom/vitest'

// Mock BroadcastChannel
if (typeof globalThis.BroadcastChannel === 'undefined') {
  globalThis.BroadcastChannel = class {
    constructor(name) {
      this.name = name
      this.onmessage = null
    }
    postMessage() {}
    close() {}
  }
}

// Mock window.scrollTo and Element.scrollIntoView
if (typeof window !== 'undefined') {
  window.scrollTo = () => {}
  window.Element.prototype.scrollIntoView = () => {}
}

// Mock HTMLAudioElement play/pause
if (typeof window !== 'undefined' && window.Audio) {
  window.Audio.prototype.play = () => Promise.resolve()
  window.Audio.prototype.pause = () => {}
}
