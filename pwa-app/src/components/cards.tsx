import { Heart, Info, MapPin, Star } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { MealPlan, Recipe, Restaurant, SavedRef } from "../types";
import { Card, Chips } from "./ui";
import { CachedImage } from "./cached-image";

export function MealCard({ item, saved, toggle, open, delay = 0, animate = true, priority = false }: { item: MealPlan; saved: boolean; toggle: (ref: SavedRef) => void; open?: () => void; delay?: number; animate?: boolean; priority?: boolean }) {
  const navigate = useNavigate();
  const activate = open || (() => navigate(`/meal/${item.id}`));
  return <Card className="result-card meal-card" delay={delay} animate={animate} onActivate={activate} ariaLabel={`查看组合菜单：${item.title}`}>
    <div className="compact-thumb warm"><CachedImage src={item.coverUrl} priority={priority} fallback={<span>套餐</span>}/></div>
    <div className="result-body"><div className="card-heading"><div><h2>{item.title}</h2><p>{item.structure} · {item.cookTime}</p></div><SaveButton saved={saved} onClick={() => toggle({ kind: "meal", id: item.id })}/></div><Chips items={item.tags.slice(0, 3)}/><button className="card-open" onClick={(event) => { event.stopPropagation(); activate(); }}>查看整套菜单</button></div>
  </Card>;
}

export function RecipeCard({ item, saved, toggle, open, delay = 0, animate = true, priority = false }: { item: Recipe; saved: boolean; toggle: (ref: SavedRef) => void; open?: () => void; delay?: number; animate?: boolean; priority?: boolean }) {
  const navigate = useNavigate();
  const activate = open || (() => navigate(`/recipe/${item.id}`));
  return <Card className="result-card recipe-card" delay={delay} animate={animate} onActivate={activate} ariaLabel={`查看菜谱：${item.name}`}>
    <div className="compact-thumb warm"><CachedImage src={item.coverUrl} priority={priority} fallback={<span>{item.name.slice(0, 1)}</span>}/></div>
    <div className="result-body"><div className="card-heading"><div><h2>{item.name}</h2><p>{item.cookTime} · {item.difficulty}</p></div><SaveButton saved={saved} onClick={() => toggle({ kind: "recipe", id: item.id })}/></div><Chips items={item.tags.slice(0, 3)}/><button className="card-open" onClick={(event) => { event.stopPropagation(); activate(); }}>查看图文做法</button></div>
  </Card>;
}

export function RestaurantCard({ item, saved, toggle, open, delay = 0, animate = true, priority = false }: { item: Restaurant; saved: boolean; toggle: (ref: SavedRef) => void; open?: () => void; delay?: number; animate?: boolean; priority?: boolean }) {
  const navigate = useNavigate();
  const activate = open || (() => navigate(`/restaurant/${item.id}`));
  return <Card className="restaurant-result" delay={delay} animate={animate} onActivate={activate} ariaLabel={`查看餐厅：${item.name}`}>
    <div className="restaurant-main"><div className="restaurant-thumb"><CachedImage src={item.coverUrl} priority={priority} fallback={<MapPin/>}/></div><div className="restaurant-copy"><div className="restaurant-title"><h2>{item.name}</h2><span>{item.distance}</span></div><div className="restaurant-meta"><b><Star fill="currentColor"/> {item.rating}</b><span>{item.price}</span><span>{item.category}</span></div><p className="reason-pill"><Heart fill="currentColor"/> {item.reason}</p></div></div>
    <div className="restaurant-actions"><button onClick={(event) => { event.stopPropagation(); activate(); }}><Info/>查看详情</button><button className={saved ? "saved" : ""} onClick={(event) => { event.stopPropagation(); toggle({ kind: "restaurant", id: item.id }); }}><Heart fill={saved ? "currentColor" : "none"}/>{saved ? "已收藏" : "收藏"}</button></div>
  </Card>;
}

function SaveButton({ saved, onClick }: { saved: boolean; onClick: () => void }) {
  return <button type="button" className={`save-button ${saved ? "saved" : ""}`} aria-label={saved ? "取消收藏" : "收藏"} onClick={(event) => { event.stopPropagation(); onClick(); }}><Heart fill={saved ? "currentColor" : "none"}/></button>;
}
