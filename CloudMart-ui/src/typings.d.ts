type CSSModuleClasses = { readonly [key: string]: string }

declare module '*.module.css' {
  const classes: CSSModuleClasses
  export default classes
}

declare module '*.module.less' {
  const classes: CSSModuleClasses
  export default classes
}

declare module '*.module.scss' {
  const classes: CSSModuleClasses
  export default classes
}

declare module 'umi' {
  import type { History } from 'history'

  export const history: History
  export function Outlet(): JSX.Element | null
  export function useLocation(): { pathname: string; search: string; hash: string; state: unknown }
  export function useParams<Params extends Record<string, string> = Record<string, string>>(): Params
  export function useSearchParams(): [URLSearchParams, (next: URLSearchParams | ((prev: URLSearchParams) => URLSearchParams)) => void]
  export function useNavigate(): (to: string | number, options?: { replace?: boolean; state?: unknown }) => void
  export function Navigate(props: { to: string; replace?: boolean; state?: unknown }): JSX.Element | null
  export function Link(props: { to: string; replace?: boolean; state?: unknown; children?: React.ReactNode }): JSX.Element | null
}
