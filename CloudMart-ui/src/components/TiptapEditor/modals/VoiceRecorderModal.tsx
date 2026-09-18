import { useEffect, useRef, useState } from 'react'
import { Button, Modal, Space, Typography } from 'antd'
import { AudioOutlined, CheckOutlined, SoundOutlined, StopOutlined } from '@ant-design/icons'
import { uploadFile } from '@/api/file'
import { message } from '@/utils/appMessage'
import styles from './modals.module.css'

/**
 * 语音录制弹窗（编辑器「语音」按钮）。
 *
 * 使用 MediaRecorder 采集麦克风；优先 webm 容器（Chrome/Edge/Firefox），
 * Safari 回退 mp4。上传走 mall-file 统一链路（受每日配额约束）。
 */

interface VoiceRecorderModalProps {
  open: boolean
  onClose: () => void
  onUploaded: (url: string, filename: string) => void
}

const MIME_CANDIDATES = ['audio/webm;codecs=opus', 'audio/webm', 'audio/mp4']

function pickMimeType(): string | null {
  if (typeof MediaRecorder === 'undefined') return null
  return MIME_CANDIDATES.find((mime) => MediaRecorder.isTypeSupported(mime)) ?? null
}

function extensionOf(mimeType: string): string {
  if (mimeType.includes('webm')) return 'webm'
  if (mimeType.includes('mp4')) return 'm4a'
  if (mimeType.includes('ogg')) return 'ogg'
  return 'wav'
}

function formatDuration(seconds: number): string {
  const mins = Math.floor(seconds / 60)
  const secs = seconds % 60
  return `${mins}:${secs.toString().padStart(2, '0')}`
}

const MAX_SECONDS = 300

export default function VoiceRecorderModal({ open, onClose, onUploaded }: VoiceRecorderModalProps) {
  const [recording, setRecording] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [audioUrl, setAudioUrl] = useState<string | null>(null)
  const [audioMime, setAudioMime] = useState<string>('')
  const [audioSize, setAudioSize] = useState(0)
  const [uploading, setUploading] = useState(false)
  const [unsupported, setUnsupported] = useState(false)

  const recorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const timerRef = useRef<ReturnType<typeof setInterval> | null>(null)
  const streamRef = useRef<MediaStream | null>(null)
  const previewRef = useRef<HTMLAudioElement | null>(null)

  const cleanup = () => {
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = null
    streamRef.current?.getTracks().forEach((track) => track.stop())
    streamRef.current = null
    recorderRef.current = null
    previewRef.current?.pause()
    previewRef.current = null
  }

  useEffect(() => {
    if (!open) {
      cleanup()
      setRecording(false)
      setSeconds(0)
      setAudioUrl(null)
      setAudioMime('')
      setAudioSize(0)
      setUploading(false)
      setUnsupported(typeof MediaRecorder === 'undefined' || !navigator.mediaDevices?.getUserMedia)
    }
    return cleanup
  }, [open])

  const stopRecording = () => {
    if (timerRef.current) clearInterval(timerRef.current)
    timerRef.current = null
    if (recorderRef.current?.state === 'recording') recorderRef.current.stop()
    streamRef.current?.getTracks().forEach((track) => track.stop())
    setRecording(false)
  }

  const startRecording = async () => {
    const mimeType = pickMimeType()
    if (!mimeType || !navigator.mediaDevices?.getUserMedia) {
      message.error('当前浏览器不支持语音录制')
      setUnsupported(true)
      return
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
      streamRef.current = stream
      const recorder = new MediaRecorder(stream, { mimeType })
      chunksRef.current = []
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) chunksRef.current.push(event.data)
      }
      recorder.onstop = () => {
        const blob = new Blob(chunksRef.current, { type: mimeType })
        setAudioUrl(URL.createObjectURL(blob))
        setAudioMime(mimeType)
        setAudioSize(blob.size)
      }
      recorder.start()
      recorderRef.current = recorder
      setRecording(true)
      setSeconds(0)
      timerRef.current = setInterval(() => {
        setSeconds((prev) => {
          if (prev + 1 >= MAX_SECONDS) {
            stopRecording()
          }
          return prev + 1
        })
      }, 1000)
    } catch {
      message.error('麦克风访问被拒绝，请检查浏览器权限')
    }
  }


  const handleConfirm = async () => {
    if (!audioUrl) return
    setUploading(true)
    try {
      const blob = await fetch(audioUrl).then((res) => res.blob())
      const file = new File([blob], `voice-${Date.now()}.${extensionOf(audioMime)}`, { type: audioMime.split(';')[0] })
      const { data: response } = await uploadFile(file)
      if (response.data?.url) {
        onUploaded(response.data.url, file.name)
        onClose()
      } else if (response.error?.code === 'UPLOAD_DAILY_LIMIT_EXCEEDED') {
        message.error(response.error.message)
      }
    } catch (error) {
      message.error(error instanceof Error ? error.message : '语音上传失败，请重试')
    } finally {
      setUploading(false)
    }
  }

  return (
    <Modal
      title="录制语音"
      open={open}
      onCancel={onClose}
      width={380}
      footer={
        unsupported ? null : (
          <Space>
            <Button onClick={onClose}>取消</Button>
            <Button
              type="primary"
              icon={<CheckOutlined />}
              disabled={!audioUrl || recording}
              loading={uploading}
              onClick={handleConfirm}
            >
              插入语音
            </Button>
          </Space>
        )
      }
      destroyOnHidden
    >
      {unsupported ? (
        <Typography.Text type="secondary">
          当前浏览器不支持录音，请使用 Chrome / Edge / Firefox / Safari 最新版。
        </Typography.Text>
      ) : (
        <div className={styles.voicePanel}>
          <div className={styles.voiceStatus}>
            {recording ? (
              <>
                <SoundOutlined spin className={styles.voiceRecordingIcon} />
                <span>录音中 {formatDuration(seconds)}</span>
              </>
            ) : audioUrl ? (
              <span>已录制 {formatDuration(seconds)}</span>
            ) : (
              <span>点击开始录音（最长 {MAX_SECONDS / 60} 分钟）</span>
            )}
          </div>
          {audioUrl && !recording && (
            <audio ref={previewRef} src={audioUrl} controls className={styles.voicePreview} />
          )}
          <Button
            type="primary"
            danger={recording}
            size="large"
            shape="circle"
            icon={recording ? <StopOutlined /> : <AudioOutlined />}
            onClick={recording ? stopRecording : startRecording}
            aria-label={recording ? '停止录音' : '开始录音'}
          />
          <Typography.Text type="secondary" style={{ fontSize: 12 }}>
            {audioSize > 0 && `${(audioSize / 1024).toFixed(0)} KB · 上传计入每日配额`}
          </Typography.Text>
        </div>
      )}
    </Modal>
  )
}
