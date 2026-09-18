import { Node, mergeAttributes } from '@tiptap/core'

/**
 * 视频附件节点（本站播放器）。
 *
 * <p>块级原子节点：视频上传（≤50MB）与外链插入共用。
 * 编辑器内以原生 video 控件预览；渲染端经 RichText 以 SiteVideoPlayer 呈现。</p>
 */

export interface MediaVideoOptions {
  HTMLAttributes: Record<string, unknown>
}

declare module '@tiptap/core' {
  interface Commands<ReturnType> {
    mediaVideo: {
      setMediaVideo: (options: { src: string; title?: string }) => ReturnType
    }
  }
}

export const MediaVideo = Node.create<MediaVideoOptions>({
  name: 'mediaVideo',
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
      { tag: 'video[src]' },
      {
        tag: 'div[data-video-src]',
        getAttrs: (element) => ({
          src: (element as HTMLElement).getAttribute('data-video-src'),
          title: (element as HTMLElement).getAttribute('data-video-title'),
        }),
      },
    ]
  },

  renderHTML({ node, HTMLAttributes }) {
    return [
      'video',
      mergeAttributes(this.options.HTMLAttributes, HTMLAttributes, {
        src: node.attrs.src,
        controls: true,
        preload: 'metadata',
        playsinline: true,
        'data-media-video': '',
      }),
    ]
  },

  addCommands() {
    return {
      setMediaVideo:
        (options) =>
        ({ commands }) =>
          commands.insertContent({
            type: this.name,
            attrs: { src: options.src, title: options.title ?? null },
          }),
    }
  },
})
