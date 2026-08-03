import { Platform } from 'react-native'

// Web fallback using localStorage
const webStorage = {
  getItem: async (key: string): Promise<string | null> => {
    return localStorage.getItem(key)
  },
  setItem: async (key: string, value: string): Promise<void> => {
    localStorage.setItem(key, value)
  },
  removeItem: async (key: string): Promise<void> => {
    localStorage.removeItem(key)
  },
  multiGet: async (keys: string[]): Promise<Array<[string, string | null]>> => {
    return keys.map((key) => [key, localStorage.getItem(key)] as [string, string | null])
  },
  multiSet: async (pairs: Array<[string, string]>): Promise<void> => {
    pairs.forEach(([key, value]) => localStorage.setItem(key, value))
  },
  multiRemove: async (keys: string[]): Promise<void> => {
    keys.forEach((key) => localStorage.removeItem(key))
  },
}

async function getNativeStorage() {
  const SecureStore = await import('expo-secure-store')
  return {
    getItem: async (key: string): Promise<string | null> => {
      try {
        return await SecureStore.getItemAsync(key)
      } catch {
        return null
      }
    },
    setItem: async (key: string, value: string): Promise<void> => {
      await SecureStore.setItemAsync(key, value)
    },
    removeItem: async (key: string): Promise<void> => {
      await SecureStore.deleteItemAsync(key)
    },
    multiGet: async (keys: string[]): Promise<Array<[string, string | null]>> => {
      return Promise.all(keys.map(async (key) => [key, await SecureStore.getItemAsync(key).catch(() => null)] as [string, string | null]))
    },
    multiSet: async (pairs: Array<[string, string]>): Promise<void> => {
      await Promise.all(pairs.map(([key, value]) => SecureStore.setItemAsync(key, value)))
    },
    multiRemove: async (keys: string[]): Promise<void> => {
      await Promise.all(keys.map((key) => SecureStore.deleteItemAsync(key).catch(() => {})))
    },
  }
}

let _nativeStorage: typeof webStorage | null = null

async function getStorage() {
  if (Platform.OS === 'web') return webStorage
  if (!_nativeStorage) {
    _nativeStorage = await getNativeStorage()
  }
  return _nativeStorage
}

export const storage = {
  getItem: async (key: string): Promise<string | null> => {
    const s = await getStorage()
    return s.getItem(key)
  },
  setItem: async (key: string, value: string): Promise<void> => {
    const s = await getStorage()
    return s.setItem(key, value)
  },
  removeItem: async (key: string): Promise<void> => {
    const s = await getStorage()
    return s.removeItem(key)
  },
  multiGet: async (keys: string[]): Promise<Array<[string, string | null]>> => {
    const s = await getStorage()
    return s.multiGet(keys)
  },
  multiSet: async (pairs: Array<[string, string]>): Promise<void> => {
    const s = await getStorage()
    return s.multiSet(pairs)
  },
  multiRemove: async (keys: string[]): Promise<void> => {
    const s = await getStorage()
    return s.multiRemove(keys)
  },
}
