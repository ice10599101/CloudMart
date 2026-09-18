import { Node, mergeAttributes } from '@tiptap/core'

/**
 * 音频附件节点（本站播放器）。
 *
 * <p>块级原子节点：语音录制、音视频上传、外链插入共用。
 * 编辑器内以原生 audio 控件预览；渲染端经 RichText 以 SiteAudioPlayer 呈现。</p>
 */

export interface MediaAudioOptions {
  HTMLAttributes: Record<string, unknown>
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    mediaAudio: {
      setMediaAudio: (options: { src: string; title?: string }) => ReturnType
    }
  }
}

export const MediaAudio = Node.create<MediaAudioOptions>({
  name: 'mediaAudio',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,

  addOptions() {
    return { HTMLAttributes: {} }
  },

  addAttributes() {
    return {
      src: { default: null },
      title: { default: null },
    }
  },

  parseHTML() {
    return [
      { tag: 'audio[src]' },
      {
        // 渲染端占位：SiteAudioPlayer 容器（粘贴/再编辑场景回填）
        tag: 'div[data-audio-src]',
        getAttrs: (element) => ({
          src: (element as HTMLElement).getAttribute('data-audio-src'),
          title: (element as HTMLElement).getAttribute('data-audio-title'),
        }),
      },
    ]
  },

  renderHTML({ node, HTMLAttributes }) {
    return [
      'audio',
      mergeAttributes(this.options.HTMLAttributes, HTMLAttributes, {
        src: node.attrs.src,
        controls: true,
        preload: 'none',
        'data-media-audio': '',
      }),
    ]
  },

  addCommands() {
    return {
      setMediaAudio:
        (options) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { src: options.src, title: options.title ?? null },
          }),
    }
  },
})
