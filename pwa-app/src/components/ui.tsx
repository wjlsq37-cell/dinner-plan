import type { ReactNode } from "react";
import { ChefHat, Heart, Home, MapPin, Settings } from "lucide-react";
import { NavLink, useNavigate } from "react-router-dom";

export function Page({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <main className={`page ${className}`}>{children}</main>;
}

export function TopBar({ title, subtitle, back = false, action }: { title: string; subtitle?: string; back?: boolean; action?: ReactNode }) {
  const navigate = useNavigate();
  return <header className="topbar">
    <div>{back && <button className="icon-button" aria-label="返回" onClick={() => navigate(-1)}>←</button>}</div>
    <div className="topbar-copy"><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
    <div>{action}</div>
  </header>;
}

export function BottomNav() {
  const links = [
    ["/", "首页", Home], ["/nearby", "附近", MapPin], ["/saved", "收藏", Heart], ["/settings", "设置", Settings]
  ] as const;
  return <nav className="bottom-nav" aria-label="主导航">{links.map(([to, label, Icon]) =>
    <NavLink key={to} to={to} end={to === "/"} className={({ isActive }) => isActive ? "active" : ""}><Icon size={21}/><span>{label}</span></NavLink>
  )}</nav>;
}

export function Card({ children, className = "", delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  return <section className={`card card-enter ${className}`} style={{ animationDelay: `${Math.min(delay, 220)}ms` }}>{children}</section>;
}

export function Chips({ items, selected = [], onToggle, tone = "warm" }: { items: string[]; selected?: string[]; onToggle?: (value: string) => void; tone?: "warm" | "mint" }) {
  return <div className="chips">{items.map((item) => <button key={item} type="button" className={`chip ${tone} ${selected.includes(item) ? "selected" : ""}`} onClick={() => onToggle?.(item)}>{item}</button>)}</div>;
}

export function EmptyState({ icon = <ChefHat/>, title, message, action, actionText }: { icon?: ReactNode; title: string; message: string; action?: () => void; actionText?: string }) {
  return <Card className="empty-state"><div className="empty-icon">{icon}</div><h2>{title}</h2><p>{message}</p>{action && <button className="button secondary" onClick={action}>{actionText || "重试"}</button>}</Card>;
}

export function LoadingPanel({ kind, elapsed, onCancel }: { kind: "ai" | "database" | "location"; elapsed?: string; onCancel?: () => void }) {
  const title = kind === "ai" ? "AI 正在推演这一桌饭" : kind === "location" ? "正在扫描附近餐厅" : "正在菜谱库里筛选";
  return <Card className={`loading-panel ${kind}`}><div className={kind === "location" ? "radar" : "shimmer-orb"}/><div><h2>{title}</h2><p>{elapsed ? `已用时 ${elapsed}，可以随时取消并保留上一次成功结果。` : "马上就好，正在整理更合适的结果。"}</p>{onCancel && <button className="text-button" onClick={onCancel}>取消制作</button>}</div></Card>;
}

export function ImageHero({ src, title, tone = "warm" }: { src?: string; title: string; tone?: "warm" | "mint" }) {
  return <div className={`image-hero ${tone}`} style={src ? { backgroundImage: `linear-gradient(0deg, rgba(20,10,5,.72), transparent), url(${JSON.stringify(src).slice(1,-1)})` } : undefined}><span>{title.slice(0, 1)}</span><h1>{title}</h1></div>;
}

export function Status({ message, tone = "info" }: { message?: string | null; tone?: "info" | "error" | "success" }) {
  if (!message) return null;
  return <div className={`status ${tone}`} role={tone === "error" ? "alert" : "status"}>{message}</div>;
}
