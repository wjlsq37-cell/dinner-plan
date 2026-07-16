import type { ReactNode } from "react";
import { ArrowLeft, ChefHat, Heart, Home, LocateFixed, Map, Settings, Sparkles } from "lucide-react";
import { NavLink, useNavigate } from "react-router-dom";

export function Page({ children, className = "" }: { children: ReactNode; className?: string }) {
  return <main className={`page ${className}`}>{children}</main>;
}

export function TopBar({ title, subtitle, back = false, action }: { title: string; subtitle?: string; back?: boolean; action?: ReactNode }) {
  const navigate = useNavigate();
  return <header className={`topbar ${back ? "with-back" : ""}`}>
    {back && <button className="icon-button" aria-label="返回" onClick={() => navigate(-1)}><ArrowLeft /></button>}
    <div className="topbar-copy"><h1>{title}</h1>{subtitle && <p>{subtitle}</p>}</div>
    <div className="topbar-action">{action}</div>
  </header>;
}

export function BottomNav() {
  const links = [["/", "首页", Home], ["/nearby", "附近", Map], ["/saved", "收藏", Heart], ["/settings", "设置", Settings]] as const;
  return <nav className="bottom-nav" aria-label="主导航">{links.map(([to, label, Icon]) =>
    <NavLink key={to} to={to} end={to === "/"} className={({ isActive }) => isActive ? "active" : ""}><span className="nav-icon"><Icon size={21}/></span><span>{label}</span></NavLink>
  )}</nav>;
}

export function Card({ children, className = "", delay = 0 }: { children: ReactNode; className?: string; delay?: number }) {
  return <section className={`card card-enter ${className}`} style={{ animationDelay: `${Math.min(delay, 220)}ms` }}>{children}</section>;
}

export function Chips({ items, selected = [], onToggle, tone = "warm" }: { items: string[]; selected?: string[]; onToggle?: (value: string) => void; tone?: "warm" | "mint" }) {
  return <div className="chips">{items.map((item) => {
    const isSelected = selected.includes(item);
    return <button key={item} type="button" aria-pressed={isSelected} className={`chip ${tone} ${isSelected ? "selected" : ""}`} onClick={() => onToggle?.(item)}>{item}</button>;
  })}</div>;
}

export function EmptyState({ icon = <ChefHat/>, title, message, action, actionText }: { icon?: ReactNode; title: string; message: string; action?: () => void; actionText?: string }) {
  return <Card className="empty-state"><div className="empty-icon">{icon}</div><h2>{title}</h2><p>{message}</p>{action && <button className="button secondary" onClick={action}>{actionText || "重试"}</button>}</Card>;
}

export function LoadingPanel({ kind, elapsed, onCancel }: { kind: "ai" | "database" | "location"; elapsed?: string; onCancel?: () => void }) {
  const title = kind === "ai" ? "AI 正在推演这一桌饭" : kind === "location" ? "正在搜索附近餐厅" : "正在菜谱库里筛选";
  const Icon = kind === "location" ? LocateFixed : kind === "ai" ? Sparkles : ChefHat;
  return <Card className={`loading-panel ${kind}`}><span className="loading-icon"><Icon /></span><div><h2>{title}</h2><p>{elapsed ? `已用时 ${elapsed}，可以随时取消并保留上一次成功结果。` : "马上就好，正在整理更合适的结果。"}</p>{onCancel && <button className="text-button" onClick={onCancel}>取消生成</button>}</div></Card>;
}

export function ImageHero({ src, title, tone = "warm" }: { src?: string; title: string; tone?: "warm" | "mint" }) {
  return <div className={`image-hero ${tone}`}>{src ? <img src={src} alt=""/> : <ChefHat aria-hidden="true"/>}<div className="image-hero-copy"><h1>{title}</h1></div></div>;
}

export function Status({ message, tone = "info" }: { message?: string | null; tone?: "info" | "error" | "success" }) {
  if (!message) return null;
  return <div className={`status ${tone}`} role={tone === "error" ? "alert" : "status"}>{message}</div>;
}
