import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Navigate, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { ChefHat, CircleHelp, Database, LocateFixed, MapPin, RefreshCw, Settings as SettingsIcon, Sparkles, Utensils } from "lucide-react";
import { ApiGateway } from "./lib/api";
import { addHistory, appendQuery, formatElapsed, friendlyError, sortRestaurants, toggleSaved } from "./lib/utils";
import { defaultState, loadState, saveState } from "./lib/store";
import { seedMealPlans, seedRecipes, seedRestaurants } from "./data/seed";
import type { CookSource, DeveloperSettings, LocationValue, MealPlan, PersistedState, Recipe, RecommendMode, Restaurant, RestaurantSort, SavedKind, SavedRef } from "./types";
import { BottomNav, Card, Chips, EmptyState, ImageHero, LoadingPanel, Page, Status, TopBar } from "./components/ui";
import { MealCard, RecipeCard, RestaurantCard } from "./components/cards";

interface AppContextValue {
  state: PersistedState;
  patch: (patch: Partial<PersistedState>) => void;
  toggle: (ref: SavedRef) => void;
  viewed: (ref: SavedRef) => void;
  isSaved: (ref: SavedRef) => boolean;
  findMeal: (id: string) => MealPlan | undefined;
  findRecipe: (id: string) => Recipe | undefined;
  findRestaurant: (id: string) => Restaurant | undefined;
}

const AppContext = createContext<AppContextValue | null>(null);
const useApp = () => { const value = useContext(AppContext); if (!value) throw new Error("App context missing"); return value; };

function AppProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<PersistedState>(structuredClone(defaultState));
  const [ready, setReady] = useState(false);
  useEffect(() => { loadState().then((saved) => { setState(saved); setReady(true); }); }, []);
  useEffect(() => { if (ready) void saveState(state); }, [state, ready]);
  const patch = useCallback((value: Partial<PersistedState>) => setState((current) => ({ ...current, ...value })), []);
  const toggle = useCallback((ref: SavedRef) => setState((current) => ({ ...current, saved: toggleSaved(current.saved, ref) })), []);
  const viewed = useCallback((ref: SavedRef) => setState((current) => ({ ...current, history: addHistory(current.history, ref) })), []);
  const value = useMemo<AppContextValue>(() => ({
    state, patch, toggle, viewed,
    isSaved: (ref) => state.saved.some((item) => item.kind === ref.kind && item.id === ref.id),
    findMeal: (id) => [...state.mealCache, ...state.lastMealPlans, ...seedMealPlans].find((item) => item.id === id),
    findRecipe: (id) => [...state.recipeCache, ...state.lastRecipes, ...seedRecipes].find((item) => item.id === id),
    findRestaurant: (id) => [...state.restaurantCache, ...state.lastRestaurants, ...seedRestaurants].find((item) => item.id === id)
  }), [state, patch, toggle, viewed]);
  if (!ready) return <div className="splash"><img src="/icon.svg" alt=""/><h1>吃点啥</h1><p>正在摆好今天的餐桌…</p></div>;
  return <AppContext.Provider value={value}>{children}</AppContext.Provider>;
}

function Layout({ children }: { children: ReactNode }) {
  const [online, setOnline] = useState(navigator.onLine);
  const [updater, setUpdater] = useState<null | (() => Promise<void>)>(null);
  useEffect(() => {
    const onOnline = () => setOnline(true); const onOffline = () => setOnline(false);
    const onUpdate = (event: Event) => setUpdater(() => (event as CustomEvent).detail);
    window.addEventListener("online", onOnline); window.addEventListener("offline", onOffline); window.addEventListener("pwa-update-ready", onUpdate);
    return () => { window.removeEventListener("online", onOnline); window.removeEventListener("offline", onOffline); window.removeEventListener("pwa-update-ready", onUpdate); };
  }, []);
  return <div className="app-shell">{!online && <div className="offline-banner">当前离线：可继续查看本地收藏和历史</div>}{updater && <button className="update-banner" onClick={() => updater()}>新版本已准备好，点击更新</button>}<div className="content-shell">{children}</div><BottomNav/></div>;
}

function HomePage() {
  const { state, isSaved, toggle, viewed, patch } = useApp(); const navigate = useNavigate();
  const [loading, setLoading] = useState(false); const [error, setError] = useState("");
  const [decision, setDecision] = useState<{ recipe?: Recipe; restaurant?: Restaurant }>({});
  const decide = async () => {
    if (!navigator.onLine) return setError("当前处于离线状态，联网后再帮你决定。");
    setLoading(true); setError(""); const gateway = new ApiGateway(state.developerSettings);
    try {
      const preferences = state.preferences; const location = state.location;
      const [cook, nearby] = await Promise.allSettled([
        gateway.cook({ query: "", mode: "RECIPE_SINGLE", cookSource: state.lastCookSource, preferences, broadSearch: true }),
        gateway.restaurants({ query: "", mode: "RESTAURANT", preferences, location, broadSearch: true })
      ]);
      const recipes = cook.status === "fulfilled" ? cook.value.recipes : state.lastRecipes;
      const restaurants = nearby.status === "fulfilled" ? nearby.value.restaurants : state.lastRestaurants;
      const recipe = recipes[Math.floor(Math.random() * recipes.length)]; const restaurant = restaurants[Math.floor(Math.random() * restaurants.length)];
      setDecision({ recipe, restaurant });
      patch({ recipeCache: [...recipes, ...state.recipeCache].filter((item, index, all) => all.findIndex((candidate) => candidate.id === item.id) === index).slice(0, 80), restaurantCache: [...restaurants, ...state.restaurantCache].filter((item, index, all) => all.findIndex((candidate) => candidate.id === item.id) === index) });
      if (!recipe && !restaurant) setError("暂时没找到合适结果，可以稍后再试。");
    } catch (cause) { setError(friendlyError(cause)); } finally { setLoading(false); }
  };
  const hour = new Date().getHours(); const greeting = hour < 11 ? "早上先吃点舒服的" : hour < 15 ? "午饭交给我来拍板" : hour < 22 ? "晚饭别再纠结了" : "这会儿吃点不费劲的";
  return <Layout><Page className="home-page">
    <TopBar title="吃点啥" subtitle={greeting} action={<button className="icon-button" aria-label="设置" onClick={() => navigate("/settings")}><SettingsIcon/></button>}/>
    <section className="hero appetite"><div className="hero-glow"/><span className="hero-kicker">TODAY'S TABLE</span><h2>把“随便吃点”<br/>变成一个好答案</h2><p>做一道顺手菜，或者去附近找一家合适的店。</p><button className="button light" disabled={loading} onClick={decide}><Sparkles/> {loading ? "正在帮你选" : "帮我决定"}</button></section>
    <Status message={error} tone="error"/>{loading && <LoadingPanel kind="ai"/>}
    {(decision.recipe || decision.restaurant) && <section className="section"><div className="section-title"><div><span>今日决策</span><h2>一菜一店，直接开选</h2></div><button className="icon-button" aria-label="再选一次" onClick={decide}><RefreshCw/></button></div><div className="responsive-grid">{decision.recipe && <RecipeCard item={decision.recipe} saved={isSaved({ kind: "recipe", id: decision.recipe.id })} toggle={toggle} open={() => { viewed({ kind: "recipe", id: decision.recipe!.id }); navigate(`/recipe/${decision.recipe!.id}`); }}/>} {decision.restaurant && <RestaurantCard item={decision.restaurant} saved={isSaved({ kind: "restaurant", id: decision.restaurant.id })} toggle={toggle} open={() => { viewed({ kind: "restaurant", id: decision.restaurant!.id }); navigate(`/restaurant/${decision.restaurant!.id}`); }}/>}</div></section>}
    <section className="decision-grid"><button className="entry-card cook" onClick={() => navigate("/cook")}><span className="entry-icon"><ChefHat/></span><small>在家做</small><h2>今晚自己做点啥</h2><p>按口味、食材和时间生成菜谱或整桌菜单。</p><b>开始选菜 →</b></button><button className="entry-card nearby" onClick={() => navigate("/nearby")}><span className="entry-icon"><MapPin/></span><small>附近吃</small><h2>看看周围有什么</h2><p>基于真实位置和高德 POI，找到能马上出发的店。</p><b>扫描附近 →</b></button></section>
    <section className="section"><div className="section-title"><div><span>今日灵感</span><h2>不想输入，也可以从这里开始</h2></div></div><div className="inspiration-row">{seedRecipes.map((recipe) => <button key={recipe.id} onClick={() => { viewed({ kind: "recipe", id: recipe.id }); navigate(`/recipe/${recipe.id}`); }}><span>{recipe.name.slice(0,1)}</span><b>{recipe.name}</b><small>{recipe.cookTime}</small></button>)}</div></section>
  </Page></Layout>;
}

function CookPage() {
  const { state, patch, toggle, isSaved, viewed } = useApp(); const navigate = useNavigate();
  const [query, setQuery] = useState(state.lastCookQuery); const [mode, setMode] = useState<RecommendMode>(state.lastCookMode); const [source, setSource] = useState<CookSource>(state.lastCookSource);
  const [loading, setLoading] = useState(false); const [elapsed, setElapsed] = useState(0); const [summary, setSummary] = useState(""); const [status, setStatus] = useState(state.lastMealPlans.length || state.lastRecipes.length ? "" : "输入需求，开始生成今天的菜单。"); const controller = useRef<AbortController | null>(null); const requestId = useRef("");
  useEffect(() => { if (!loading) return; const timer = setInterval(() => setElapsed((value) => value + 1), 1000); return () => clearInterval(timer); }, [loading]);
  const search = async (broadSearch = false) => {
    if (!query.trim() && !broadSearch) return setStatus("请输入想吃什么，或点击随便推荐。");
    if (!navigator.onLine) return setStatus("当前处于离线状态，仍可浏览上一次成功结果。");
    controller.current?.abort(); controller.current = new AbortController(); requestId.current = crypto.randomUUID(); setLoading(true); setElapsed(0); setStatus("");
    try {
      const result = await new ApiGateway(state.developerSettings).cook({ query, mode, cookSource: source, requestId: requestId.current, broadSearch, preferences: state.preferences }, controller.current.signal);
      const meals = result.mealPlans || []; const recipes = result.recipes || []; setSummary(result.summary || ""); setStatus(result.fallbackReason || (meals.length || recipes.length ? "" : "没有找到合适结果，换个关键词试试。"));
      patch({ lastCookQuery: query, lastCookMode: mode, lastCookSource: source, lastMealPlans: meals, lastRecipes: recipes, mealCache: [...meals, ...state.mealCache].filter((v,i,a)=>a.findIndex(x=>x.id===v.id)===i), recipeCache: [...recipes, ...state.recipeCache].filter((v,i,a)=>a.findIndex(x=>x.id===v.id)===i).slice(0,80) });
    } catch (cause) { if (!(cause instanceof DOMException && cause.name === "AbortError")) setStatus(friendlyError(cause)); } finally { setLoading(false); }
  };
  const cancel = async () => { controller.current?.abort(); setLoading(false); setStatus("请求已取消，已保留上一次成功结果。"); await new ApiGateway(state.developerSettings).cancel(requestId.current).catch(() => undefined); };
  return <Layout><Page><TopBar title="在家做点啥" subtitle="一桌饭，也可以很快决定" back/>
    <Card className="search-panel"><div className="segmented"><button className={mode === "RECIPE_COMBO" ? "active" : ""} onClick={() => setMode("RECIPE_COMBO")}>组合菜单</button><button className={mode === "RECIPE_SINGLE" ? "active" : ""} onClick={() => setMode("RECIPE_SINGLE")}>单道菜</button></div><label className="search-field"><ChefHat/><textarea value={query} onChange={(event) => setQuery(event.target.value)} placeholder="例如：两荤一素、一汤、主食、微辣" rows={3}/></label><Chips items={["两荤一素", "一汤", "主食", "微辣", "快手菜"]} onToggle={(value) => setQuery((current) => appendQuery(current, value))}/><div className="source-row"><label><Database/><input type="radio" checked={source === "DATABASE"} onChange={() => setSource("DATABASE")}/> 菜谱库</label><label><Sparkles/><input type="radio" checked={source === "AI_GENERATED"} onChange={() => setSource("AI_GENERATED")}/> AI 生成</label></div><div className="button-row"><button className="button grow" onClick={() => search(false)} disabled={loading}>{source === "AI_GENERATED" ? "生成这一桌" : "搜索菜谱"}</button><button className="button secondary" onClick={() => search(true)} disabled={loading}><RefreshCw/> 随便推荐</button></div></Card>
    {loading && <LoadingPanel kind={source === "AI_GENERATED" ? "ai" : "database"} elapsed={formatElapsed(elapsed)} onCancel={cancel}/>}<Status message={status} tone={status.includes("取消") ? "info" : "error"}/>{summary && <Card className="summary-card"><Sparkles/><div><span>推荐理解</span><p>{summary}</p></div></Card>}
    <div className="results">{mode === "RECIPE_COMBO" && state.lastMealPlans.map((item,index)=><MealCard key={item.id} item={item} saved={isSaved({kind:"meal",id:item.id})} toggle={toggle} delay={index*55} open={()=>{viewed({kind:"meal",id:item.id});navigate(`/meal/${item.id}`);}}/>)}{state.lastRecipes.map((item,index)=><RecipeCard key={item.id} item={item} saved={isSaved({kind:"recipe",id:item.id})} toggle={toggle} delay={index*55} open={()=>{viewed({kind:"recipe",id:item.id});navigate(`/recipe/${item.id}`);}}/>)}</div>
  </Page></Layout>;
}

function NearbyPage() {
  const { state, patch, toggle, isSaved, viewed } = useApp(); const navigate = useNavigate();
  const [query, setQuery] = useState(state.lastRestaurantQuery); const [location, setLocation] = useState(state.location.text || ""); const [sort, setSort] = useState<RestaurantSort>("relevance"); const [loading, setLoading] = useState(false); const [status, setStatus] = useState(""); const controller = useRef<AbortController | null>(null);
  const search = async (nextLocation: LocationValue = { ...state.location, text: location }, broadSearch = false) => {
    if (!query.trim() && !broadSearch) return setStatus("请输入想找的餐厅，或点击随便看看。"); if (!navigator.onLine) return setStatus("当前处于离线状态，仍可浏览上一次结果。");
    controller.current?.abort(); controller.current = new AbortController(); setLoading(true); setStatus("");
    try { const result = await new ApiGateway(state.developerSettings).restaurants({ query, mode: "RESTAURANT", location: nextLocation, preferences: state.preferences, broadSearch }, controller.current.signal); const items = result.restaurants || []; const used = result.locationUsed || nextLocation; setLocation(used.text || location); setStatus(result.fallbackReason || (items.length ? "" : "附近暂未找到符合条件的餐厅。")); patch({ location: used, lastRestaurantQuery: query, lastRestaurants: items, restaurantCache: [...items,...state.restaurantCache].filter((v,i,a)=>a.findIndex(x=>x.id===v.id)===i) }); }
    catch(cause){ if (!(cause instanceof DOMException && cause.name === "AbortError")) setStatus(friendlyError(cause)); } finally { setLoading(false); }
  };
  const locate = () => { if (!navigator.geolocation) return setStatus("当前浏览器不支持定位，请手动输入地点。"); setLoading(true); navigator.geolocation.getCurrentPosition((position) => { const next = { latitude: position.coords.latitude, longitude: position.coords.longitude, text: "当前位置" }; setLocation("当前位置"); void search(next, true); }, () => { setLoading(false); setStatus("定位权限未开启，你仍然可以手动输入城市、商圈或地标搜索。"); }, { enableHighAccuracy: true, timeout: 12000, maximumAge: 300000 }); };
  const openNavigation = (item: Restaurant) => { const label = encodeURIComponent(item.name); const address = encodeURIComponent(item.address); const isAndroid = /Android/i.test(navigator.userAgent); if (isAndroid && item.latitude != null && item.longitude != null) { window.location.href = `androidamap://route?sourceApplication=chidian&dlat=${item.latitude}&dlon=${item.longitude}&dname=${label}&dev=0&t=0`; window.setTimeout(() => window.open(`https://uri.amap.com/navigation?to=${item.longitude},${item.latitude},${label}&mode=car&policy=1&src=chidian`, "_blank", "noopener"), 900); } else window.open(item.latitude != null && item.longitude != null ? `https://uri.amap.com/navigation?to=${item.longitude},${item.latitude},${label}&mode=car&policy=1&src=chidian` : `https://uri.amap.com/search?keyword=${label}%20${address}&src=chidian`, "_blank", "noopener"); };
  const results = sortRestaurants(state.lastRestaurants, sort);
  return <Layout><Page><TopBar title="附近吃点啥" subtitle="真实位置 · 高德餐饮数据"/>
    <Card className="location-card"><div className="location-copy"><span className="location-pin"><MapPin/></span><div><small>当前搜索位置</small><b>{location || "还没有位置"}</b>{state.location.latitude && <span>{state.location.latitude.toFixed(4)}, {state.location.longitude?.toFixed(4)}</span>}</div></div><button className="button mint small" onClick={locate} disabled={loading}><LocateFixed/> 定位</button></Card>
    <Card className="search-panel nearby-panel"><label className="search-field"><MapPin/><input value={location} onChange={(event)=>setLocation(event.target.value)} placeholder="城市、商圈、地址或地标"/></label><label className="search-field"><Utensils/><textarea value={query} onChange={(event)=>setQuery(event.target.value)} placeholder="例如：适合一个人吃的牛肉面" rows={2}/></label><Chips items={["牛肉面", "川湘菜", "一个人", "人均 50 内", "营业中"]} tone="mint" onToggle={(value)=>setQuery((current)=>appendQuery(current,value))}/><div className="button-row"><button className="button grow" onClick={()=>search({text:location},false)} disabled={loading}>搜索附近餐厅</button><button className="button secondary" onClick={()=>search({text:location},true)} disabled={loading}><RefreshCw/> 随便看看</button></div></Card>
    {loading && <LoadingPanel kind="location"/>}<Status message={status} tone="error"/>{results.length > 0 && <div className="sort-row"><b>找到 {results.length} 家</b><div className="segmented compact"><button className={sort==="relevance"?"active":""} onClick={()=>setSort("relevance")}>综合</button><button className={sort==="distance"?"active":""} onClick={()=>setSort("distance")}>距离</button><button className={sort==="rating"?"active":""} onClick={()=>setSort("rating")}>评分</button></div></div>}
    <div className="results">{results.map((item,index)=><RestaurantCard key={item.id} item={item} saved={isSaved({kind:"restaurant",id:item.id})} toggle={toggle} delay={index*55} navigateTo={()=>openNavigation(item)} open={()=>{viewed({kind:"restaurant",id:item.id});navigate(`/restaurant/${item.id}`);}}/>)}</div>{!loading && !results.length && !status && <EmptyState icon={<MapPin/>} title="附近雷达还没有结果" message="输入菜系、预算、用餐场景或距离，开始搜索真实餐厅。"/>}
  </Page></Layout>;
}

function DetailPage({ kind }: { kind: SavedKind }) {
  const { id = "" } = useParams(); const { state, patch, toggle, isSaved, findMeal, findRecipe, findRestaurant, viewed } = useApp(); const navigate = useNavigate();
  const [loading, setLoading] = useState(false); const [error, setError] = useState(""); let item: MealPlan | Recipe | Restaurant | undefined = kind === "meal" ? findMeal(id) : kind === "recipe" ? findRecipe(id) : findRestaurant(id);
  useEffect(() => { viewed({ kind, id }); }, [kind, id, viewed]);
  useEffect(() => { if (kind !== "recipe" || !item || (item as Recipe).stepImageUrls?.length || (item as Recipe).source === "seed" || !navigator.onLine) return; const controller = new AbortController(); setLoading(true); new ApiGateway(state.developerSettings).recipe(id, controller.signal).then((detail)=>patch({recipeCache:[detail,...state.recipeCache.filter(x=>x.id!==detail.id)]})).catch((cause)=>setError(friendlyError(cause))).finally(()=>setLoading(false)); return ()=>controller.abort(); }, [id, kind]);
  if (!item) return <Layout><Page><TopBar title="详情" back/><EmptyState title="没有找到这条内容" message="它可能已从缓存中清理，请返回重新搜索。" action={()=>navigate(-1)} actionText="返回"/></Page></Layout>;
  if (kind === "meal") { const meal = item as MealPlan; return <Layout><Page><TopBar title="组合菜单" back/><ImageHero src={meal.coverUrl} title={meal.title}/><DetailMeta items={[meal.structure,meal.cookTime,meal.servings]}/><Card><h2>这一桌怎么搭</h2><div className="dish-list">{meal.dishes.map((dish)=><button key={`${dish.course}-${dish.name}`} disabled={!dish.recipeId} onClick={()=>dish.recipeId&&navigate(`/recipe/${dish.recipeId}`)}><span>{dish.course}</span><div><b>{dish.name}</b><small>{dish.note}</small></div>{dish.recipeId&&"→"}</button>)}</div></Card><Card><h2>统一采购清单</h2><ul className="check-list">{meal.shoppingList.map(value=><li key={value}>{value}</li>)}</ul></Card><Card><h2>建议烹饪顺序</h2><ol className="timeline">{meal.timeline.map(value=><li key={value}>{value}</li>)}</ol></Card><button className="button sticky-action" onClick={()=>toggle({kind,id})}>{isSaved({kind,id})?"已收藏整套菜单":"收藏整套菜单"}</button></Page></Layout>; }
  if (kind === "recipe") { const recipe = item as Recipe; return <Layout><Page><TopBar title="菜谱详情" back/><ImageHero src={recipe.coverUrl} title={recipe.name}/><DetailMeta items={[recipe.cuisine,recipe.cookTime,recipe.difficulty,recipe.servings]}/><Status message={error} tone="error"/>{loading&&<LoadingPanel kind="database"/>}<Card><h2>准备食材</h2><div className="ingredient-grid">{recipe.ingredients.map((value,index)=><div key={`${value.name}-${index}`}><b>{value.name}</b><span>{value.amount}</span></div>)}</div></Card><Card><h2>跟着步骤做</h2><ol className="recipe-steps">{recipe.steps.map((step,index)=><li key={`${step}-${index}`}><span>{index+1}</span><div>{recipe.stepImageUrls?.[index]&&<img src={recipe.stepImageUrls[index]} alt={`第 ${index+1} 步`} loading="lazy"/>}<p>{step}</p></div></li>)}</ol></Card><Card className="tip-card"><CircleHelp/><div><h2>烹饪小技巧</h2><p>{recipe.tips}</p></div></Card><button className="button sticky-action" onClick={()=>toggle({kind,id})}>{isSaved({kind,id})?"已收藏菜谱":"收藏菜谱"}</button></Page></Layout>; }
  const restaurant = item as Restaurant; const navUrl = restaurant.latitude!=null&&restaurant.longitude!=null?`https://uri.amap.com/navigation?to=${restaurant.longitude},${restaurant.latitude},${encodeURIComponent(restaurant.name)}&mode=car&src=chidian`:`https://uri.amap.com/search?keyword=${encodeURIComponent(restaurant.name+" "+restaurant.address)}&src=chidian`;
  return <Layout><Page><TopBar title="餐厅详情" back/><ImageHero src={restaurant.coverUrl} title={restaurant.name} tone="mint"/><DetailMeta items={[restaurant.category,restaurant.distance,restaurant.rating,restaurant.price]}/><Card><h2>为什么推荐</h2><p>{restaurant.reason}</p><Chips items={restaurant.tags} tone="mint"/></Card><Card><h2>门店信息</h2><dl className="details"><div><dt>地址</dt><dd>{restaurant.address}</dd></div><div><dt>营业</dt><dd>{restaurant.open}</dd></div><div><dt>电话</dt><dd>{restaurant.phone}</dd></div><div><dt>来源</dt><dd>{restaurant.source}</dd></div></dl></Card><div className="sticky-row"><button className="button secondary" onClick={()=>toggle({kind,id})}>{isSaved({kind,id})?"已收藏":"收藏"}</button><button className="button grow" onClick={()=>window.open(navUrl,"_blank","noopener")}>导航去这里</button></div></Page></Layout>;
}

function DetailMeta({items}:{items:string[]}){return <div className="detail-meta">{items.filter(Boolean).map(value=><span key={value}>{value}</span>)}</div>}

function SavedPage() {
  const { state, toggle, isSaved, findMeal, findRecipe, findRestaurant, viewed }=useApp(); const navigate=useNavigate(); const [filter,setFilter]=useState<"all"|"cook"|"restaurant">("all"); const refs=state.saved.filter(ref=>filter==="all"||(filter==="restaurant"?ref.kind==="restaurant":ref.kind!=="restaurant"));
  return <Layout><Page><TopBar title="我的收藏" subtitle="收藏会保存在这台设备"/><div className="segmented saved-filter"><button className={filter==="all"?"active":""} onClick={()=>setFilter("all")}>全部</button><button className={filter==="cook"?"active":""} onClick={()=>setFilter("cook")}>自己做</button><button className={filter==="restaurant"?"active":""} onClick={()=>setFilter("restaurant")}>餐厅</button></div><div className="results">{refs.map((ref,index)=>{if(ref.kind==="meal"){const item=findMeal(ref.id);return item&&<MealCard key={`${ref.kind}-${ref.id}`} item={item} saved={isSaved(ref)} toggle={toggle} delay={index*45} open={()=>{viewed(ref);navigate(`/meal/${ref.id}`)}}/>}if(ref.kind==="recipe"){const item=findRecipe(ref.id);return item&&<RecipeCard key={`${ref.kind}-${ref.id}`} item={item} saved={isSaved(ref)} toggle={toggle} delay={index*45} open={()=>{viewed(ref);navigate(`/recipe/${ref.id}`)}}/>}const item=findRestaurant(ref.id);return item&&<RestaurantCard key={`${ref.kind}-${ref.id}`} item={item} saved={isSaved(ref)} toggle={toggle} delay={index*45} open={()=>{viewed(ref);navigate(`/restaurant/${ref.id}`)}}/>})}</div>{!refs.length&&<EmptyState title="还没有收藏" message="看到喜欢的组合菜单、菜谱或餐厅，点收藏就会出现在这里。"/>}<section className="section"><div className="section-title"><div><span>最近浏览</span><h2>再看一眼刚才的选择</h2></div></div><div className="history-list">{state.history.map(ref=>{const title=ref.kind==="meal"?findMeal(ref.id)?.title:ref.kind==="recipe"?findRecipe(ref.id)?.name:findRestaurant(ref.id)?.name;return title&&<button key={`${ref.kind}-${ref.id}`} onClick={()=>navigate(`/${ref.kind}/${ref.id}`)}><span>{ref.kind==="restaurant"?<MapPin/>:<ChefHat/>}</span><b>{title}</b><small>再次打开 →</small></button>})}</div></section></Page></Layout>;
}

function SettingsPage() {
  const {state,patch}=useApp(); const navigate=useNavigate(); const preferences=state.preferences;
  const updatePreferences=(value:Partial<typeof preferences>)=>patch({preferences:{...preferences,...value}});
  return <Layout><Page><TopBar title="偏好设置" subtitle="让每次推荐更像你"/><Card><div className="settings-heading"><div><span>口味偏好</span><h2>我平时喜欢</h2></div></div><EditableTags values={preferences.tastes} options={["微辣","麻辣","清淡","酸甜","咸鲜","低脂"]} onChange={(tastes)=>updatePreferences({tastes})}/></Card><Card><div className="settings-heading"><div><span>忌口管理</span><h2>推荐时请避开</h2></div></div><EditableTags values={preferences.avoids} options={["香菜","海鲜","牛羊肉","葱蒜","辣椒"]} onChange={(avoids)=>updatePreferences({avoids})}/></Card><Card><label className="field-label">默认搜索半径<select value={preferences.defaultDistanceKm} onChange={(event)=>updatePreferences({defaultDistanceKm:Number(event.target.value)})}><option value={1}>1km</option><option value={3}>3km</option><option value={5}>5km</option><option value={10}>10km</option></select></label><Toggle label="优先推荐快手菜" checked={preferences.preferQuickRecipes} onChange={(preferQuickRecipes)=>updatePreferences({preferQuickRecipes})}/><Toggle label="优先推荐营业中的餐厅" checked={preferences.preferOpenRestaurants} onChange={(preferOpenRestaurants)=>updatePreferences({preferOpenRestaurants})}/></Card><button className="developer-link" onClick={()=>navigate("/settings/developer")}><span><SettingsIcon/></span><div><b>开发者设置</b><small>后端、AI、高德与菜谱 API</small></div>→</button></Page></Layout>;
}

function EditableTags({values,options,onChange}:{values:string[];options:string[];onChange:(v:string[])=>void}){const [draft,setDraft]=useState("");const all=[...new Set([...options,...values])];return <><Chips items={all} selected={values} onToggle={value=>onChange(values.includes(value)?values.filter(x=>x!==value):[...values,value])}/><div className="inline-add"><input value={draft} onChange={e=>setDraft(e.target.value)} placeholder="添加自定义标签"/><button onClick={()=>{const value=draft.trim();if(value&&!values.includes(value))onChange([...values,value]);setDraft("")}}>添加</button></div></>}
function Toggle({label,checked,onChange}:{label:string;checked:boolean;onChange:(v:boolean)=>void}){return <label className="toggle-row"><span>{label}</span><input type="checkbox" checked={checked} onChange={e=>onChange(e.target.checked)}/></label>}

function DeveloperPage(){const {state,patch}=useApp();const settings=state.developerSettings;const update=(value:Partial<DeveloperSettings>)=>patch({developerSettings:{...settings,...value}});return <Layout><Page><TopBar title="开发者设置" subtitle="密钥仅保存在当前浏览器" back/><Status message="开发者模式的密钥通过 HTTPS 临时发送给同源代理，不会进入 Service Worker 缓存或服务端存储。"/><Card><Toggle label="开启开发者直连模式" checked={settings.enabled} onChange={enabled=>update({enabled})}/><p className="muted">关闭时使用现有 Vercel 后端；开启时使用下面的 AI、高德与菜谱配置。</p></Card><fieldset disabled={!settings.enabled}><SettingsGroup title="AI 设置"><label className="field-label">服务商<select value={settings.aiProvider} onChange={e=>update({aiProvider:e.target.value as DeveloperSettings["aiProvider"]})}><option value="deepseek">DeepSeek</option><option value="custom">自定义 AI</option></select></label><TextField label="AI Base URL" value={settings.aiBaseUrl} onChange={aiBaseUrl=>update({aiBaseUrl})}/><TextField label="AI API Key" type="password" value={settings.aiApiKey} onChange={aiApiKey=>update({aiApiKey})}/><TextField label="模型" value={settings.aiModel} onChange={aiModel=>update({aiModel})}/></SettingsGroup><SettingsGroup title="地图 API 设置"><TextField label="高德 Web Key" type="password" value={settings.amapWebKey} onChange={amapWebKey=>update({amapWebKey})}/></SettingsGroup><SettingsGroup title="菜谱 API 设置"><label className="field-label">数据源<select value={settings.recipeApiSource} onChange={e=>update({recipeApiSource:e.target.value as DeveloperSettings["recipeApiSource"]})}><option value="wanwei">万维易源</option><option value="mxnzp">mxnzp</option><option value="custom">自定义</option></select></label><TextField label="菜谱 API Base URL" value={settings.recipeApiBaseUrl} onChange={recipeApiBaseUrl=>update({recipeApiBaseUrl})}/><TextField label="App ID" value={settings.recipeApiAppId} onChange={recipeApiAppId=>update({recipeApiAppId})}/><TextField label="App Secret" type="password" value={settings.recipeApiSecret} onChange={recipeApiSecret=>update({recipeApiSecret})}/><TextField label="万维 App Key" type="password" value={settings.wanweiRecipeAppKey} onChange={wanweiRecipeAppKey=>update({wanweiRecipeAppKey})}/><label className="field-label">单次条数<input type="number" min={1} max={50} value={settings.recipePageSize} onChange={e=>update({recipePageSize:Math.min(50,Math.max(1,Number(e.target.value)))})}/></label></SettingsGroup><SettingsGroup title="请求设置"><label className="field-label">最长等待时间（秒）<input type="number" min={10} max={300} value={settings.maxWaitSeconds} onChange={e=>update({maxWaitSeconds:Math.min(300,Math.max(10,Number(e.target.value)))})}/></label></SettingsGroup></fieldset></Page></Layout>}
function SettingsGroup({title,children}:{title:string;children:ReactNode}){return <Card className="settings-group"><h2>{title}</h2><div className="field-stack">{children}</div></Card>}
function TextField({label,value,onChange,type="text"}:{label:string;value:string;onChange:(v:string)=>void;type?:string}){return <label className="field-label">{label}<input type={type} value={value} autoComplete="off" onChange={e=>onChange(e.target.value)}/></label>}

export default function App(){return <AppProvider><Routes><Route path="/" element={<HomePage/>}/><Route path="/cook" element={<CookPage/>}/><Route path="/nearby" element={<NearbyPage/>}/><Route path="/meal/:id" element={<DetailPage kind="meal"/>}/><Route path="/recipe/:id" element={<DetailPage kind="recipe"/>}/><Route path="/restaurant/:id" element={<DetailPage kind="restaurant"/>}/><Route path="/saved" element={<SavedPage/>}/><Route path="/settings" element={<SettingsPage/>}/><Route path="/settings/developer" element={<DeveloperPage/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Routes></AppProvider>}
