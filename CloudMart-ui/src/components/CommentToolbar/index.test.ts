import { describe, expect, it } from 'vitest'
import { splitCommentImages } from '@/utils/commentImages'

describe('splitCommentImages', () => {
  it('recognizes uploaded /files image paths and restores the image segment', () => {
    expect(splitCommentImages('好用！\n![图片](/files/pic/20261020/photo.jpg)')).toEqual([
      { type: 'text', value: '好用！\n' },
      { type: 'image', value: '/files/pic/20261020/photo.jpg' },
    ])
  })

  it('continues to support standard markdown images with a custom alt label', () => {
    expect(splitCommentImages('![截图](https://cdn.example.com/photo.jpg)')).toEqual([
      { type: 'image', value: 'https://cdn.example.com/photo.jpg' },
    ])
  })

  it('supports legacy image links whose exclamation mark was stripped', () => {
    expect(splitCommentImages('[图片](/files/pic/20261020/photo.jpg)')).toEqual([
      { type: 'image', value: '/files/pic/20261020/photo.jpg' },
    ])
  })

  it('keeps unsupported URL schemes as plain text', () => {
    expect(splitCommentImages('![图片](javascript:alert(1))')).toEqual([
      { type: 'text', value: '![图片](javascript:alert(1))' },
    ])
  })
})
