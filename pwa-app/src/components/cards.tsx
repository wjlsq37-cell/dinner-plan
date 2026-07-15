import { Clock3, Heart, MapPin, Navigation, Star, Users } from "lucide-react";
import { useNavigate } from "react-router-dom";
import type { MealPlan, Recipe, Restaurant, SavedRef } from "../types";
import { Card, Chips } from "./ui";

export function MealCard({ item, saved, toggle, open, delay = 0 }: { item: MealPlan; saved: boolean; toggle: (ref: SavedRef) => void; open?: () => void; delay?: number }) {
  const navigate = useNavigate();
  return <Card className="result-card meal-card" delay={delay}>
    <div className="card-heading"><div><span className="eyebrow">整桌方案</span><h2>{item.title}</h2></div><button className={`save-button ${saved ? "saved" : ""}`} aria-label={saved ? "取消收藏" : "收藏"} onClick={() => toggle({ kind: "meal", id: item.id })}><Heart fill={saved ? "currentColor" : "none"}/></button></div>
    <p>{item.reason}</p><Chips items={item.tags}/><div className="meta"><span><Clock3/> {item.cookTime}</span><span><Users/> {item.servings}</span></div>
    <div className="dish-preview">{item.dishes.slice(0, 4).map((dish) => <span key={`${dish.course}-${dish.name}`}><b>{dish.course}</b>{dish.name}</span>)}</div>
    <button className="button secondary full" onClick={open || (() => navigate(`/meal/${item.id}`))}>查看整套菜单</button>
  </Card>;
}

export function RecipeCard({ item, saved, toggle, open, delay = 0 }: { item: Recipe; saved: boolean; toggle: (ref: SavedRef) => void; open?: () => void; delay?: number }) {
  const navigate = useNavigate();
  return <Card className="result-card recipe-card" delay={delay}>
    <div className="thumbnail warm">{item.coverUrl ? <img src={item.coverUrl} alt="" loading="lazy"/> : <span>{item.name.slice(0,1)}</span>}</div>
    <div className="result-body"><div className="card-heading"><div><span className="eyebrow">{item.cuisine}</span><h2>{item.name}</h2></div><button className={`save-button ${saved ? "saved" : ""}`} aria-label={saved ? "取消收藏" : "收藏"} onClick={() => toggle({ kind: "recipe", id: item.id })}><Heart fill={saved ? "currentColor" : "none"}/></button></div>
    <p>{item.reason}</p><div className="meta"><span><Clock3/> {item.cookTime}</span>{item.ratingStars && <span><Star/> {item.ratingStars.toFixed(1)}</span>}</div><Chips items={item.tags.slice(0,3)}/>
    <button className="text-link" onClick={open || (() => navigate(`/recipe/${item.id}`))}>查看图文做法 →</button></div>
  </Card>;
}

export function RestaurantCard({ item, saved, toggle, open, navigateTo, delay = 0 }: { item: Restaurant; saved: boolean; toggle: (ref: SavedRef) => void; open?: () => void; navigateTo?: () => void; delay?: number }) {
  const navigate = useNavigate();
  return <Card className="result-card restaurant-card" delay={delay}>
    <div className="thumbnail mint">{item.coverUrl ? <img src={item.coverUrl} alt="" loading="lazy"/> : <MapPin/>}</div>
    <div className="result-body"><div className="card-heading"><div><span className="eyebrow location">高德真实 POI · {item.category}</span><h2>{item.name}</h2></div><button className={`save-button ${saved ? "saved" : ""}`} aria-label={saved ? "取消收藏" : "收藏"} onClick={() => toggle({ kind: "restaurant", id: item.id })}><Heart fill={saved ? "currentColor" : "none"}/></button></div>
    <p>{item.address}</p><div className="meta"><span><Navigation/> {item.distance}</span><span><Star/> {item.rating}</span><span>{item.price}</span></div><Chips items={item.tags.slice(0,3)} tone="mint"/>
    <div className="row-actions"><button className="text-link" onClick={open || (() => navigate(`/restaurant/${item.id}`))}>查看详情 →</button>{navigateTo && <button className="button small" onClick={navigateTo}>导航</button>}</div></div>
  </Card>;
}
