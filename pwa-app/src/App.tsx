import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { Navigate, Route, Routes, useNavigate, useParams } from "react-router-dom";
import { CircleHelp, Database, Heart, Info, LocateFixed, MapPin, Plus, RefreshCw, Save, Search, Settings as SettingsIcon, SlidersHorizontal, Sparkles, Star, Trash2, Utensils, X } from "lucide-react";
import { ApiGateway } from "./lib/api";
import { requestBrowserLocation, LocationRequestError } from "./lib/location";
import { dismissPwaUpdate, subscribePwaUpdate, type PwaUpdater } from "./lib/pwa-update";
import { addHistory, appendQuery, formatElapsed, friendlyError, mergeRecipeRecords, sortRestaurants, toggleSaved, toggleString } from "./lib/utils";
import { defaultState, loadState, saveState } from "./lib/store";
import { markExternalNavigation, resumeExternalNavigation } from "./lib/app-resume";
import { readPageMemory, restorePageScroll, writePageMemory } from "./lib/page-memory";
import { seedMealPlans, seedRecipes, seedRestaurants } from "./data/seed";
import type { CookSource, DeveloperSettings, LocationValue, MealPlan, PersistedState, Recipe, RecommendMode, Restaurant, RestaurantSort, SavedKind, SavedRef, ServiceStatus, UserPreference } from "./types";
import { BottomNav, Card, Chips, EmptyState, ImageHero, LoadingPanel, Page, Status, TopBar } from "./components/ui";
import { MealCard, RecipeCard, RestaurantCard } from "./components/cards";
import { CachedImage } from "./components/cached-image";

interface CookTaskState {
  loading: boolean;
  startedAt: number | null;
  status: string;
  summary: string;
  source: CookSource;
  revision: number;
  requestId: string;
}

interface RestaurantTaskState {
  loading: boolean;
  startedAt: number | null;
  status: string;
  revision: number;
  location?: LocationValue;
}

interface StartCookTask {
  query: string;
  mode: RecommendMode;
  source: CookSource;
  broadSearch: boolean;
}

interface StartRestaurantTask {
  query: string;
  location: LocationValue;
  broadSearch: boolean;
  notice?: string;
}

interface AppContextValue {
  state: PersistedState;
  patch: (patch: Partial<PersistedState>) => void;
  mergeMeals: (items: MealPlan[]) => void;
  mergeRecipes: (items: Recipe[]) => void;
  mergeRestaurants: (items: Restaurant[]) => void;
  updatePreferences: (value: Partial<UserPreference> | ((current: UserPreference) => Partial<UserPreference>)) => void;
  toggleTaste: (value: string) => void;
  toggleAvoid: (value: string) => void;
  setDefaultDistance: (value: number) => void;
  updateDeveloperSettings: (value: Partial<DeveloperSettings>) => void;
  toggle: (ref: SavedRef) => void;
  viewed: (ref: SavedRef) => void;
  isSaved: (ref: SavedRef) => boolean;
  findMeal: (id: string) => MealPlan | undefined;
  findRecipe: (id: string) => Recipe | undefined;
  findRestaurant: (id: string) => Restaurant | undefined;
  cookTask: CookTaskState;
  startCookTask: (task: StartCookTask) => void;
  cancelCookTask: () => void;
  restaurantTask: RestaurantTaskState;
  startRestaurantTask: (task: StartRestaurantTask) => void;
}

const AppContext = createContext<AppContextValue | null>(null);
const useApp = () => { const value = useContext(AppContext); if (!value) throw new Error("App context missing"); return value; };

function AppProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<PersistedState>(structuredClone(defaultState));
  const [ready, setReady] = useState(false);
  const [cookTask, setCookTask] = useState<CookTaskState>({ loading: false, startedAt: null, status: "", summary: "", source: "DATABASE", revision: 0, requestId: "" });
  const [restaurantTask, setRestaurantTask] = useState<RestaurantTaskState>({ loading: false, startedAt: null, status: "", revision: 0 });
  const stateRef = useRef(state);
  const cookController = useRef<AbortController | null>(null);
  const cookRun = useRef(0);
  const restaurantController = useRef<AbortController | null>(null);
  const restaurantRun = useRef(0);
  stateRef.current = state;
  useEffect(() => { loadState().then((saved) => { setState(saved); setReady(true); }); }, []);
  useEffect(() => { if (ready) void saveState(state); }, [state, ready]);
  const patch = useCallback((value: Partial<PersistedState>) => setState((current) => ({ ...current, ...value })), []);
  const mergeMeals = useCallback((items: MealPlan[]) => setState((current) => ({ ...current, mealCache: uniqueById([...items, ...current.mealCache]) })), []);
  const mergeRecipes = useCallback((items: Recipe[]) => setState((current) => ({ ...current, recipeCache: mergeRecipeRecords(current.recipeCache, items, current.lastRecipes).slice(0, 80) })), []);
  const mergeRestaurants = useCallback((items: Restaurant[]) => setState((current) => ({ ...current, restaurantCache: uniqueById([...items, ...current.restaurantCache]) })), []);
  const updatePreferences = useCallback<AppContextValue["updatePreferences"]>((value) => setState((current) => {
    const next = typeof value === "function" ? value(current.preferences) : value;
    return { ...current, preferences: { ...current.preferences, ...next } };
  }), []);
  const toggleTaste = useCallback((value: string) => updatePreferences((current) => ({ tastes: toggleString(current.tastes, value) })), [updatePreferences]);
  const toggleAvoid = useCallback((value: string) => updatePreferences((current) => ({ avoids: toggleString(current.avoids, value) })), [updatePreferences]);
  const setDefaultDistance = useCallback((value: number) => updatePreferences((current) => ({ defaultDistanceKm: current.defaultDistanceKm === value ? null : value })), [updatePreferences]);
  const updateDeveloperSettings = useCallback((value: Partial<DeveloperSettings>) => setState((current) => ({ ...current, developerSettings: { ...current.developerSettings, ...value } })), []);
  const toggle = useCallback((ref: SavedRef) => setState((current) => ({ ...current, saved: toggleSaved(current.saved, ref) })), []);
  const viewed = useCallback((ref: SavedRef) => setState((current) => ({ ...current, history: addHistory(current.history, ref) })), []);
  const startCookTask = useCallback((task: StartCookTask) => {
    if (!navigator.onLine) {
      setCookTask((current) => ({ ...current, loading: false, status: "当前处于离线状态，仍可浏览上一次成功结果。" }));
      return;
    }
    cookController.current?.abort();
    const controller = new AbortController();
    const run = ++cookRun.current;
    const requestId = crypto.randomUUID();
    const snapshot = stateRef.current;
    cookController.current = controller;
    setCookTask((current) => ({ ...current, loading: true, startedAt: Date.now(), status: "", summary: "", source: task.source, requestId }));
    void new ApiGateway(snapshot.developerSettings).cook({ query: task.query, mode: task.mode, cookSource: task.source, requestId, broadSearch: task.broadSearch, preferences: snapshot.preferences }, controller.signal).then((result) => {
      if (run !== cookRun.current) return;
      const meals = result.mealPlans || [];
      const recipes = result.recipes || [];
      setState((current) => ({
        ...current,
        lastCookQuery: "",
        lastCookMode: task.mode,
        lastCookSource: task.source,
        lastMealPlans: meals,
        lastRecipes: recipes,
        mealCache: uniqueById([...meals, ...current.mealCache]),
        recipeCache: mergeRecipeRecords(current.recipeCache, recipes, current.lastRecipes).slice(0, 80)
      }));
      setCookTask((current) => ({ ...current, loading: false, startedAt: null, summary: result.summary || "", status: result.fallbackReason || (meals.length || recipes.length ? "" : "没有找到合适结果，换个关键词试试。"), revision: current.revision + 1 }));
    }).catch((cause) => {
      if (run !== cookRun.current || controller.signal.aborted) return;
      setCookTask((current) => ({ ...current, loading: false, startedAt: null, status: friendlyError(cause) }));
    });
  }, []);
  const cancelCookTask = useCallback(() => {
    const requestId = cookTask.requestId;
    const settings = stateRef.current.developerSettings;
    cookRun.current += 1;
    cookController.current?.abort();
    cookController.current = null;
    setCookTask((current) => ({ ...current, loading: false, startedAt: null, status: "请求已取消，已保留上一次成功结果。" }));
    if (requestId) void new ApiGateway(settings).cancel(requestId).catch(() => undefined);
  }, [cookTask.requestId]);
  const startRestaurantTask = useCallback((task: StartRestaurantTask) => {
    if (!navigator.onLine) {
      setRestaurantTask((current) => ({ ...current, loading: false, status: "当前处于离线状态，仍可浏览上一次结果。" }));
      return;
    }
    restaurantController.current?.abort();
    const controller = new AbortController();
    const run = ++restaurantRun.current;
    const snapshot = stateRef.current;
    restaurantController.current = controller;
    setRestaurantTask((current) => ({ ...current, loading: true, startedAt: Date.now(), status: "" }));
    void new ApiGateway(snapshot.developerSettings).restaurants({ query: task.query, mode: "RESTAURANT", location: task.location, preferences: snapshot.preferences, broadSearch: task.broadSearch }, controller.signal).then((result) => {
      if (run !== restaurantRun.current) return;
      const items = result.restaurants || [];
      const used = result.locationUsed || task.location;
      const nextLocation = { ...used, text: used.text || task.location.text || "" };
      const resultStatus = result.fallbackReason || (items.length ? "根据搜索内容、距离和评分为你整理附近餐厅" : "附近暂未找到符合条件的餐厅。");
      setState((current) => ({
        ...current,
        location: nextLocation,
        lastRestaurantQuery: "",
        lastRestaurants: items,
        restaurantCache: uniqueById([...items, ...current.restaurantCache])
      }));
      setRestaurantTask((current) => ({ ...current, loading: false, startedAt: null, status: task.notice ? `${task.notice} ${resultStatus}` : resultStatus, revision: current.revision + 1, location: nextLocation }));
    }).catch((cause) => {
      if (run !== restaurantRun.current || controller.signal.aborted) return;
      setRestaurantTask((current) => ({ ...current, loading: false, startedAt: null, status: friendlyError(cause) }));
    });
  }, []);
  const context = useMemo<AppContextValue>(() => ({
    state, patch, mergeMeals, mergeRecipes, mergeRestaurants, updatePreferences, toggleTaste, toggleAvoid, setDefaultDistance, updateDeveloperSettings, toggle, viewed, cookTask, startCookTask, cancelCookTask, restaurantTask, startRestaurantTask,
    isSaved: (ref) => state.saved.some((item) => item.kind === ref.kind && item.id === ref.id),
    findMeal: (id) => [...state.mealCache, ...state.lastMealPlans, ...seedMealPlans].find((item) => item.id === id),
    findRecipe: (id) => [...state.recipeCache, ...state.lastRecipes, ...seedRecipes].find((item) => item.id === id),
    findRestaurant: (id) => [...state.restaurantCache, ...state.lastRestaurants, ...seedRestaurants].find((item) => item.id === id)
  }), [state, patch, mergeMeals, mergeRecipes, mergeRestaurants, updatePreferences, toggleTaste, toggleAvoid, setDefaultDistance, updateDeveloperSettings, toggle, viewed, cookTask, startCookTask, cancelCookTask, restaurantTask, startRestaurantTask]);
  if (!ready) return <div className="splash"><img src="/icon.svg" alt=""/><h1>吃点啥</h1><p>正在摆好今天的餐桌…</p></div>;
  return <AppContext.Provider value={context}>{children}</AppContext.Provider>;
}

function Layout({ children }: { children: ReactNode }) {
  const [online, setOnline] = useState(navigator.onLine);
  const [updater, setUpdater] = useState<PwaUpdater | null>(null);
  useEffect(() => {
    const onOnline = () => setOnline(true); const onOffline = () => setOnline(false);
    const onResume = () => { setOnline(navigator.onLine); resumeExternalNavigation(); };
    const onVisibility = () => { if (document.visibilityState === "visible") onResume(); };
    const unsubscribeUpdate = subscribePwaUpdate((next) => setUpdater(() => next));
    window.addEventListener("online", onOnline); window.addEventListener("offline", onOffline);
    window.addEventListener("pageshow", onResume); document.addEventListener("visibilitychange", onVisibility);
    return () => { window.removeEventListener("online", onOnline); window.removeEventListener("offline", onOffline); window.removeEventListener("pageshow", onResume); document.removeEventListener("visibilitychange", onVisibility); unsubscribeUpdate(); };
  }, []);
  return <div className="app-shell">{!online && <div className="offline-banner">当前离线：可继续查看本地收藏、历史和详情</div>}{updater && <div className="update-banner"><span>发现新版本，是否立即更新？</span><div><button onClick={() => { dismissPwaUpdate(); void updater(true); }}>更新</button><button onClick={() => { dismissPwaUpdate(); setUpdater(null); }}>稍后</button></div></div>}<div className="content-shell">{children}</div><BottomNav/></div>;
}

function HomePage() {
  const { state, isSaved, toggle, viewed, mergeRecipes, mergeRestaurants } = useApp(); const navigate = useNavigate();
  const [loading, setLoading] = useState(false); const [error, setError] = useState("");
  const [decision, setDecision] = useState<{ recipe?: Recipe; restaurant?: Restaurant }>({});
  const decide = async () => {
    if (!navigator.onLine) return setError("当前处于离线状态，联网后再帮你决定。");
    setLoading(true); setError(""); const gateway = new ApiGateway(state.developerSettings);
    try {
      const [cook, nearby] = await Promise.allSettled([
        gateway.cook({ query: "", mode: "RECIPE_SINGLE", cookSource: state.lastCookSource, preferences: state.preferences, broadSearch: true }),
        gateway.restaurants({ query: "", mode: "RESTAURANT", preferences: state.preferences, location: state.location, broadSearch: true })
      ]);
      const recipes = cook.status === "fulfilled" ? cook.value.recipes : state.lastRecipes;
      const restaurants = nearby.status === "fulfilled" ? nearby.value.restaurants : state.lastRestaurants;
      const recipe = recipes[Math.floor(Math.random() * recipes.length)]; const restaurant = restaurants[Math.floor(Math.random() * restaurants.length)];
      setDecision({ recipe, restaurant });
      mergeRecipes(recipes); mergeRestaurants(restaurants);
      if (!recipe && !restaurant) setError("暂时没找到合适结果，可以稍后再试。");
    } catch (cause) { setError(friendlyError(cause)); } finally { setLoading(false); }
  };
  const hour = new Date().getHours();
  const greeting = hour < 11 ? "早上先吃点舒服的" : hour < 15 ? "午饭交给我来拍板" : hour < 22 ? "晚饭别再纠结了" : "这会儿吃点不费劲的";
  return <Layout><Page className="home-page">
    <TopBar title="吃点啥" subtitle="食欲在前，AI 在背后发力" action={<button className="icon-button" aria-label="设置" onClick={() => navigate("/settings")}><SettingsIcon/></button>}/>
    <Card className="decision-card"><div className="decision-kicker"><span><Sparkles/></span><b>今日决策</b><small>少纠结一点</small></div><h2>{greeting}</h2><p>少一点选择压力，先给你一个可以马上看的答案。</p><button className="button full" disabled={loading} onClick={decide}><Sparkles/>{loading ? "正在生成今日灵感..." : "帮我决定吃什么"}</button></Card>
    <Status message={error} tone="error"/>
    <div className="home-actions"><button className="home-action" onClick={() => navigate("/cook")}><span className="food"><Utensils/></span><b>自己做</b><small>菜谱和成套晚餐</small></button><button className="home-action" onClick={() => navigate("/nearby")}><span className="place"><MapPin/></span><b>附近吃</b><small>按位置找顺路好店</small></button></div>
    <section className="inspiration"><div className="section-heading"><div><h2>今日灵感</h2><p>一个下厨方案，一个附近选择</p></div><Sparkles/></div>{loading ? <LoadingPanel kind="ai"/> : decision.recipe || decision.restaurant ? <div className="results">{decision.recipe && <RecipeCard item={decision.recipe} priority saved={isSaved({ kind: "recipe", id: decision.recipe.id })} toggle={toggle} open={() => { viewed({ kind: "recipe", id: decision.recipe!.id }); navigate(`/recipe/${decision.recipe!.id}`); }}/>} {decision.restaurant && <RestaurantCard item={decision.restaurant} priority saved={isSaved({ kind: "restaurant", id: decision.restaurant.id })} toggle={toggle} open={() => { viewed({ kind: "restaurant", id: decision.restaurant!.id }); navigate(`/restaurant/${decision.restaurant!.id}`); }}/>}</div> : <Card className="inspiration-empty"><span><Sparkles/></span><div><b>今天还没拍板</b><p>点上方按钮后，这里会出现一菜一店</p></div></Card>}</section>
  </Page></Layout>;
}

function useElapsed(active: boolean, startedAt: number | null): number {
  const [elapsed, setElapsed] = useState(0);
  useEffect(() => {
    if (!active || startedAt == null) { setElapsed(0); return; }
    const update = () => setElapsed(Math.max(0, Math.floor((Date.now() - startedAt) / 1000)));
    update();
    const timer = window.setInterval(update, 1000);
    return () => window.clearInterval(timer);
  }, [active, startedAt]);
  return elapsed;
}

function CookPage() {
  type CookMemory = { scrollY: number; hasRenderedResults: boolean; mode: RecommendMode; source: CookSource };
  const { state, toggle, isSaved, viewed, cookTask, startCookTask, cancelCookTask } = useApp(); const navigate = useNavigate();
  const restored = useRef(readPageMemory<CookMemory>("cook"));
  const [query, setQuery] = useState(""); const [mode, setMode] = useState<RecommendMode>(restored.current?.mode ?? state.lastCookMode); const [source, setSource] = useState<CookSource>(restored.current?.source ?? state.lastCookSource);
  const [notice, setNotice] = useState(""); const initialRevision = useRef(cookTask.revision); const elapsed = useElapsed(cookTask.loading, cookTask.startedAt);
  const viewRef = useRef({ mode, source }); viewRef.current = { mode, source };
  useEffect(() => { restorePageScroll("cook"); return () => writePageMemory<CookMemory>("cook", { ...viewRef.current, scrollY: window.scrollY, hasRenderedResults: true }); }, []);
  const chooseSource = (next: CookSource) => { setSource(next); setNotice(next === "AI_GENERATED" ? "已切换到 AI 生成，点击生成推荐开始；下方仍保留上一次成功结果。" : "已切换到菜谱库，点击搜索菜谱开始；下方仍保留上一次成功结果。"); };
  const search = (broadSearch = false) => {
    if (!query.trim() && !broadSearch) return setNotice("请输入想吃什么，或点击重新推荐。");
    if (!navigator.onLine) return setNotice("当前处于离线状态，仍可浏览上一次成功结果。");
    setNotice("");
    startCookTask({ query, mode, source, broadSearch });
  };
  const status = notice || cookTask.status || (state.lastMealPlans.length || state.lastRecipes.length ? "" : "输入需求，开始生成今天的菜单。");
  const animateResults = cookTask.revision > initialRevision.current || !restored.current?.hasRenderedResults;
  return <Layout><Page><TopBar title="在家做点啥" subtitle="菜谱和成套晚餐" back/>
    <Card className="search-panel"><div className="segmented"><button className={mode === "RECIPE_COMBO" ? "active" : ""} onClick={() => setMode("RECIPE_COMBO")}>组合菜单</button><button className={mode === "RECIPE_SINGLE" ? "active" : ""} onClick={() => setMode("RECIPE_SINGLE")}>单道菜</button></div><label className="search-field"><Search/><textarea value={query} onChange={(event) => setQuery(event.target.value)} placeholder="例如：两荤一素、一汤、主食、微辣" rows={3}/></label><Chips items={["两荤一素", "一汤", "主食", "微辣", "快手菜"]} onToggle={(value) => setQuery((current) => appendQuery(current, value))}/><div className="source-row"><label><input type="radio" checked={source === "DATABASE"} onChange={() => chooseSource("DATABASE")}/><Database/> 菜谱库</label><label><input type="radio" checked={source === "AI_GENERATED"} onChange={() => chooseSource("AI_GENERATED")}/><Sparkles/> AI 生成</label></div><div className="button-row"><button className="button grow" onClick={() => search(false)} disabled={cookTask.loading} aria-busy={cookTask.loading}>{source === "AI_GENERATED" ? "生成推荐" : "搜索菜谱"}</button><button className="button secondary" onClick={() => search(true)} disabled={cookTask.loading}><RefreshCw/>重新推荐</button></div></Card>
    {cookTask.loading && <LoadingPanel kind={cookTask.source === "AI_GENERATED" ? "ai" : "database"} elapsed={formatElapsed(elapsed)} onCancel={cancelCookTask}/>}<Status message={status} tone={status.includes("取消") || status.includes("输入") || status.includes("已切换") ? "info" : "error"}/>{cookTask.summary && <Status message={cookTask.summary}/>}<div className="results">{mode === "RECIPE_COMBO" && state.lastMealPlans.map((item, index) => <MealCard key={item.id} item={item} priority={index < 2} animate={animateResults} saved={isSaved({ kind: "meal", id: item.id })} toggle={toggle} delay={index * 55} open={() => { viewed({ kind: "meal", id: item.id }); navigate(`/meal/${item.id}`); }}/>) }{state.lastRecipes.map((item, index) => <RecipeCard key={item.id} item={item} priority={index < 2} animate={animateResults} saved={isSaved({ kind: "recipe", id: item.id })} toggle={toggle} delay={index * 55} open={() => { viewed({ kind: "recipe", id: item.id }); navigate(`/recipe/${item.id}`); }}/>)}</div>
  </Page></Layout>;
}

function NearbyPage() {
  type NearbyMemory = { scrollY: number; hasRenderedResults: boolean; location: string; sort: RestaurantSort };
  const { state, patch, toggle, isSaved, viewed, restaurantTask, startRestaurantTask } = useApp(); const navigate = useNavigate();
  const restored = useRef(readPageMemory<NearbyMemory>("nearby"));
  const [query, setQuery] = useState(""); const [location, setLocation] = useState(state.location.text ?? restored.current?.location ?? ""); const [editingLocation, setEditingLocation] = useState(false); const [sort, setSort] = useState<RestaurantSort>(restored.current?.sort ?? "relevance"); const [locating, setLocating] = useState(false); const [locationStartedAt, setLocationStartedAt] = useState<number | null>(null); const [notice, setNotice] = useState("");
  const initialRevision = useRef(restaurantTask.revision); const loading = locating || restaurantTask.loading; const elapsed = useElapsed(loading, locating ? locationStartedAt : restaurantTask.startedAt);
  const viewRef = useRef({ location, sort }); viewRef.current = { location, sort };
  useEffect(() => { restorePageScroll("nearby"); return () => writePageMemory<NearbyMemory>("nearby", { ...viewRef.current, scrollY: window.scrollY, hasRenderedResults: true }); }, []);
  useEffect(() => { if (state.location.text) setLocation(state.location.text); }, [state.location.text]);
  const search = (nextLocation: LocationValue = { ...state.location, text: location }, broadSearch = false, nextNotice = "") => {
    if (!query.trim() && !broadSearch) return setNotice("请输入菜系、预算、用餐场景或距离。");
    if (!navigator.onLine) return setNotice("当前处于离线状态，仍可浏览上一次结果。");
    setNotice(""); setEditingLocation(false);
    startRestaurantTask({ query, location: nextLocation, broadSearch, notice: nextNotice });
  };
  const locate = async () => {
    setLocating(true); setLocationStartedAt(Date.now()); setNotice("");
    try {
      const coordinates = await requestBrowserLocation(); const latitude = Number(coordinates.latitude); const longitude = Number(coordinates.longitude); const gateway = new ApiGateway(state.developerSettings);
      let next: LocationValue; let nextNotice = "";
      try { next = (await gateway.reverseGeocode({ latitude, longitude })).location; }
      catch { next = { latitude, longitude, text: `已定位（${latitude.toFixed(4)}, ${longitude.toFixed(4)}）` }; nextNotice = "已获得定位坐标，但暂时无法解析地址名称。"; }
      setLocation(next.text || ""); patch({ location: next }); setLocating(false); setLocationStartedAt(null); search(next, true, nextNotice);
    }
    catch (error) { setLocating(false); setLocationStartedAt(null); setNotice(error instanceof LocationRequestError ? error.message : "暂时无法获取位置，请手动输入地点。"); }
  };
  const results = sortRestaurants(state.lastRestaurants, sort);
  const status = notice || restaurantTask.status;
  const animateResults = restaurantTask.revision > initialRevision.current || !restored.current?.hasRenderedResults;
  return <Layout><Page><Card className="nearby-header"><div className="nearby-title"><h1>附近吃点啥</h1><button className="icon-button locate-button" aria-label="定位" onClick={locate} disabled={loading} aria-busy={loading}><LocateFixed/></button></div><div className="current-location"><MapPin/><span title={location || undefined}>{location || "还没有位置"}</span><button onClick={() => setEditingLocation((value) => !value)}>更改</button></div>{editingLocation && <div className="manual-location"><input value={location} onChange={(event) => setLocation(event.target.value)} placeholder="城市、商圈、地址或地标"/><button onClick={() => search({ text: location }, true)}>确定</button></div>}<div className="nearby-search"><Search/><input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="可输入菜系、预算、用餐场景或距离"/><button onClick={() => search({ ...state.location, text: location }, false)} disabled={loading} aria-busy={loading}>搜索</button></div></Card>
    <div className="sort-pills"><button className={sort === "relevance" ? "active" : ""} onClick={() => setSort("relevance")}><Heart fill="currentColor"/>综合推荐</button><button className={sort === "distance" ? "active" : ""} onClick={() => setSort("distance")}><MapPin/>距离最近</button><button className={sort === "rating" ? "active" : ""} onClick={() => setSort("rating")}><Star fill="currentColor"/>评分最高</button></div>
    {loading && <LoadingPanel kind="location" elapsed={formatElapsed(elapsed)}/>}<Status message={status} tone={status.includes("整理") ? "success" : "error"}/><div className="results">{results.map((item, index) => <RestaurantCard key={item.id} item={item} priority={index < 2} animate={animateResults} saved={isSaved({ kind: "restaurant", id: item.id })} toggle={toggle} delay={index * 55} open={() => { viewed({ kind: "restaurant", id: item.id }); navigate(`/restaurant/${item.id}`); }}/>)}</div>{!loading && !results.length && !status && <EmptyState icon={<MapPin/>} title="附近还没有结果" message="输入菜系、预算、用餐场景或距离，开始搜索真实餐厅。"/>}
  </Page></Layout>;
}

function DetailPage({ kind }: { kind: SavedKind }) {
  const { id = "" } = useParams(); const { state, mergeRecipes, toggle, isSaved, findMeal, findRecipe, findRestaurant, viewed } = useApp(); const navigate = useNavigate();
  const [loading, setLoading] = useState(false); const [error, setError] = useState(""); const detailRequested = useRef(""); const item = kind === "meal" ? findMeal(id) : kind === "recipe" ? findRecipe(id) : findRestaurant(id);
  useEffect(() => { viewed({ kind, id }); }, [kind, id, viewed]);
  useEffect(() => { if (kind !== "recipe" || !item || (item as Recipe).stepImageUrls?.length || (item as Recipe).source === "seed" || !navigator.onLine || detailRequested.current === id) return; detailRequested.current = id; const controller = new AbortController(); setLoading(true); new ApiGateway(state.developerSettings).recipe(id, controller.signal).then((detail) => mergeRecipes([detail])).catch((cause) => setError(friendlyError(cause))).finally(() => setLoading(false)); return () => controller.abort(); }, [id, item, kind, mergeRecipes, state.developerSettings]);
  if (!item) return <Layout><Page><TopBar title="详情" back/><EmptyState title="没有找到这条内容" message="它可能已从缓存中清理，请返回重新搜索。" action={() => navigate(-1)} actionText="返回"/></Page></Layout>;
  if (kind === "meal") { const meal = item as MealPlan; return <Layout><Page><TopBar title="组合菜单详情" subtitle="一桌饭的完整安排" back/><ImageHero src={meal.coverUrl} title={meal.title}/><DetailMeta items={[meal.structure, meal.cookTime, meal.servings]}/><Card><h2>这一桌怎么搭</h2><div className="dish-list">{meal.dishes.map((dish) => <button key={`${dish.course}-${dish.name}`} disabled={!dish.recipeId} onClick={() => dish.recipeId && navigate(`/recipe/${dish.recipeId}`)}><span>{dish.course}</span><div><b>{dish.name}</b><small>{dish.note}</small></div>{dish.recipeId && "›"}</button>)}</div></Card><Card><h2>统一采购清单</h2><ul className="check-list">{meal.shoppingList.map((value) => <li key={value}>{value}</li>)}</ul></Card><Card><h2>建议烹饪顺序</h2><ol className="timeline">{meal.timeline.map((value) => <li key={value}>{value}</li>)}</ol></Card><button className="button sticky-action" onClick={() => toggle({ kind, id })}>{isSaved({ kind, id }) ? "取消收藏" : "收藏整套菜单"}</button></Page></Layout>; }
  if (kind === "recipe") { const recipe = item as Recipe; return <Layout><Page><TopBar title="菜谱详情" subtitle="食材、步骤和小技巧" back/><ImageHero src={recipe.coverUrl} title={recipe.name}/><DetailMeta items={[recipe.cuisine, recipe.cookTime, recipe.difficulty, recipe.servings]}/><Status message={error} tone="error"/>{loading && <LoadingPanel kind="database"/>}<Card><h2>准备食材</h2><div className="ingredient-grid">{recipe.ingredients.map((value, index) => <div key={`${value.name}-${index}`}><b>{value.name}</b><span>{value.amount}</span></div>)}</div></Card><Card><h2>跟着步骤做</h2><ol className="recipe-steps">{recipe.steps.map((step, index) => <li key={`${step}-${index}`}><span>{index + 1}</span><div>{recipe.stepImageUrls?.[index] && <CachedImage src={recipe.stepImageUrls[index]} alt={`第 ${index + 1} 步`} priority={index === 0} fallback={<span className="step-image-placeholder">步骤图片暂不可用</span>}/>}<p>{step}</p></div></li>)}</ol></Card><Card className="tip-card"><CircleHelp/><div><h2>烹饪小技巧</h2><p>{recipe.tips}</p></div></Card><button className="button sticky-action" onClick={() => toggle({ kind, id })}>{isSaved({ kind, id }) ? "取消收藏" : "收藏菜谱"}</button></Page></Layout>; }
  const restaurant = item as Restaurant; const navUrl = restaurant.latitude != null && restaurant.longitude != null ? `https://uri.amap.com/navigation?to=${restaurant.longitude},${restaurant.latitude},${encodeURIComponent(restaurant.name)}&mode=car&src=chidian` : `https://uri.amap.com/search?keyword=${encodeURIComponent(`${restaurant.name} ${restaurant.address}`)}&src=chidian`;
  return <Layout><Page><TopBar title="餐厅详情" subtitle="门店信息与导航" back/><ImageHero src={restaurant.coverUrl} title={restaurant.name} tone="mint"/><DetailMeta items={[restaurant.category, restaurant.distance, restaurant.rating, restaurant.price]}/><Card><h2>为什么推荐</h2><p>{restaurant.reason}</p><Chips items={restaurant.tags} tone="mint"/></Card><Card><h2>门店信息</h2><dl className="details"><div><dt>地址</dt><dd>{restaurant.address}</dd></div><div><dt>营业</dt><dd>{restaurant.open}</dd></div><div><dt>电话</dt><dd>{restaurant.phone}</dd></div><div><dt>来源</dt><dd>{restaurant.source}</dd></div></dl></Card><div className="sticky-row"><button className="button secondary" onClick={() => toggle({ kind, id })}>{isSaved({ kind, id }) ? "已收藏" : "收藏"}</button><a className="button grow" href={navUrl} target="_blank" rel="noopener noreferrer" data-map-navigation onClick={(event) => markExternalNavigation(event.currentTarget)}>导航去这里</a></div></Page></Layout>;
}

function DetailMeta({ items }: { items: string[] }) { return <div className="detail-meta">{items.filter(Boolean).map((value) => <span key={value}>{value}</span>)}</div>; }

function SavedPage() {
  type SavedMemory = { scrollY: number; hasRenderedResults: boolean; filter: "all" | "cook" | "restaurant" };
  const { state, toggle, isSaved, findMeal, findRecipe, findRestaurant, viewed } = useApp(); const navigate = useNavigate(); const restored = useRef(readPageMemory<SavedMemory>("saved")); const [filter, setFilter] = useState<"all" | "cook" | "restaurant">(restored.current?.filter ?? "all"); const filterRef = useRef(filter); filterRef.current = filter; const refs = state.saved.filter((ref) => filter === "all" || (filter === "restaurant" ? ref.kind === "restaurant" : ref.kind !== "restaurant"));
  useEffect(() => { restorePageScroll("saved"); return () => writePageMemory<SavedMemory>("saved", { filter: filterRef.current, scrollY: window.scrollY, hasRenderedResults: true }); }, []);
  return <Layout><Page><TopBar title="收藏" subtitle="组合菜单、菜谱和餐厅都在这里" back action={<Info/>}/><Card className="saved-tabs"><button className={filter === "all" ? "active" : ""} onClick={() => setFilter("all")}>全部</button><button className={filter === "restaurant" ? "active" : ""} onClick={() => setFilter("restaurant")}>餐厅</button><button className={filter === "cook" ? "active" : ""} onClick={() => setFilter("cook")}>自己做</button></Card><div className="results">{refs.map((ref, index) => { const item = ref.kind === "meal" ? findMeal(ref.id) : ref.kind === "recipe" ? findRecipe(ref.id) : findRestaurant(ref.id); return item && <SavedCompactCard key={`${ref.kind}-${ref.id}`} kind={ref.kind} item={item} priority={index < 2} animate={!restored.current?.hasRenderedResults} saved={isSaved(ref)} delay={index * 45} onToggle={() => toggle(ref)} onOpen={() => { viewed(ref); navigate(`/${ref.kind}/${ref.id}`); }}/>; })}</div>{!refs.length && <EmptyState title="还没有收藏" message="看到喜欢的组合菜单、菜谱或餐厅，点收藏就会出现在这里。"/>}</Page></Layout>;
}

function SavedCompactCard({ kind, item, saved, delay, animate, priority, onToggle, onOpen }: { kind: SavedKind; item: MealPlan | Recipe | Restaurant; saved: boolean; delay: number; animate: boolean; priority: boolean; onToggle: () => void; onOpen: () => void }) {
  const title = kind === "meal" ? (item as MealPlan).title : kind === "recipe" ? (item as Recipe).name : (item as Restaurant).name;
  const subtitle = kind === "meal" ? `${(item as MealPlan).structure} · ${(item as MealPlan).cookTime}` : kind === "recipe" ? `${(item as Recipe).cookTime} · ${(item as Recipe).difficulty}` : `${(item as Restaurant).distance} · ${(item as Restaurant).price}`;
  const tags = item.tags.slice(0, 3); const image = item.coverUrl;
  return <article className={`saved-compact card interactive-card ${animate ? "card-enter" : ""}`} style={animate ? { animationDelay: `${Math.min(delay, 220)}ms` } : undefined} role="link" tabIndex={0} aria-label={`查看${title}`} onClick={onOpen} onKeyDown={(event) => { if (event.currentTarget !== event.target || (event.key !== "Enter" && event.key !== " ")) return; event.preventDefault(); onOpen(); }}><div className="saved-main"><div className={`saved-thumb ${kind === "restaurant" ? "mint" : "warm"}`}><CachedImage src={image} priority={priority} fallback={kind === "restaurant" ? <MapPin/> : <span>{kind === "meal" ? "套餐" : title.slice(0, 1)}</span>}/></div><div className="saved-copy"><h2>{title}</h2><p>{subtitle}</p><div className="chips">{tags.map((tag) => <span className="chip" key={tag}>{tag}</span>)}</div></div></div><button className="save-button saved" aria-label="取消收藏" onClick={(event) => { event.stopPropagation(); onToggle(); }}><Heart fill={saved ? "currentColor" : "none"}/></button></article>;
}

function SettingsPage() {
  const { state, updatePreferences, toggleTaste, toggleAvoid, setDefaultDistance, updateDeveloperSettings } = useApp(); const navigate = useNavigate(); const [saved, setSaved] = useState(false);
  const commit = () => { void saveState(state); setSaved(true); window.setTimeout(() => setSaved(false), 1500); };
  return <Layout><Page><TopBar title="偏好设置" subtitle="口味、忌口和搜索习惯都保存在本机" back action={<button className="icon-button" aria-label="保存" onClick={commit}><Save/></button>}/>{saved && <Status message="偏好已保存在当前设备" tone="success"/>}<button className="developer-entry" onClick={() => navigate("/settings/developer")}><span><SlidersHorizontal/></span><div><b>开发者模式</b><small>{state.developerSettings.enabled ? "已开启：使用直连服务配置" : "已关闭：继续通过线上后端服务"}</small></div><em>{state.developerSettings.enabled ? "开启" : "关闭"}</em></button>
    <PreferenceEditor title="常用口味" values={state.preferences.tastes} options={["微辣", "麻辣", "清淡", "酸甜"]} onToggle={toggleTaste} onAdd={(value) => updatePreferences((current) => ({ tastes: current.tastes.includes(value) ? current.tastes : [...current.tastes, value] }))} onDelete={(value) => updatePreferences((current) => ({ tastes: current.tastes.filter((item) => item !== value) }))}/>
    <PreferenceEditor title="忌口" values={state.preferences.avoids} options={["香菜", "海鲜", "牛羊肉", "葱蒜"]} onToggle={toggleAvoid} onAdd={(value) => updatePreferences((current) => ({ avoids: current.avoids.includes(value) ? current.avoids : [...current.avoids, value] }))} onDelete={(value) => updatePreferences((current) => ({ avoids: current.avoids.filter((item) => item !== value) }))}/>
    <Card className="preference-card"><h2>默认搜索半径</h2><Chips items={["1km", "3km", "5km", "10km"]} selected={state.preferences.defaultDistanceKm == null ? [] : [`${state.preferences.defaultDistanceKm}km`]} onToggle={(value) => setDefaultDistance(Number(value.replace("km", "")))}/></Card>
    <Card className="toggle-card"><Toggle label="优先推荐快手菜" checked={state.preferences.preferQuickRecipes} onChange={(preferQuickRecipes) => updatePreferences({ preferQuickRecipes })}/><Toggle label="只看营业中餐厅" checked={state.preferences.preferOpenRestaurants} onChange={(preferOpenRestaurants) => updatePreferences({ preferOpenRestaurants })}/></Card>
  </Page></Layout>;
}

function PreferenceEditor({ title, values, options, onToggle, onAdd, onDelete }: { title: string; values: string[]; options: string[]; onToggle: (value: string) => void; onAdd: (value: string) => void; onDelete: (value: string) => void }) {
  const [adding, setAdding] = useState(false); const [editing, setEditing] = useState(false); const [draft, setDraft] = useState(""); const all = [...new Set([...options, ...values])];
  const create = () => { const value = draft.trim(); if (value) onAdd(value); setDraft(""); setAdding(false); };
  return <Card className="preference-card"><div className="preference-heading"><h2>{title}</h2><div><button aria-label={`新增${title}`} onClick={() => { setAdding((value) => !value); setEditing(false); }}><Plus/></button><button aria-label={`编辑${title}`} className={editing ? "active" : ""} onClick={() => { setEditing((value) => !value); setAdding(false); }}><Trash2/></button></div></div>{editing ? <div className="editable-tags">{all.map((value) => <span key={value}>{value}{!options.includes(value) && <button aria-label={`删除${value}`} onClick={() => onDelete(value)}><X/></button>}</span>)}</div> : <Chips items={all} selected={values} onToggle={onToggle}/>} {adding && <div className="inline-add"><input autoFocus value={draft} onChange={(event) => setDraft(event.target.value)} onKeyDown={(event) => { if (event.key === "Enter") create(); }} placeholder={`添加${title}`}/><button onClick={create}>添加</button></div>}</Card>;
}

function Toggle({ label, checked, onChange }: { label: string; checked: boolean; onChange: (value: boolean) => void }) { return <label className="toggle-row"><span>{label}</span><input type="checkbox" checked={checked} onChange={(event) => onChange(event.target.checked)}/></label>; }

function DeveloperPage() {
  const { state, updateDeveloperSettings } = useApp(); const settings = state.developerSettings; const [service, setService] = useState<ServiceStatus | null>(null); const [checking, setChecking] = useState(false); const [saved, setSaved] = useState(false);
  const checkService = async () => { setChecking(true); try { setService(await new ApiGateway(settings).status()); } catch (error) { setService({ proxyReachable: false, backendConfigured: false, message: friendlyError(error) }); } finally { setChecking(false); } };
  const commit = () => { void saveState(state); setSaved(true); window.setTimeout(() => setSaved(false), 1500); };
  return <Layout><Page><TopBar title="开发者模式" subtitle={settings.enabled ? "当前由浏览器直连第三方服务" : "当前通过线上后端服务"} back action={<button className="icon-button" aria-label="保存" onClick={commit}><Save/></button>}/>{saved && <Status message="开发者设置已保存在当前浏览器" tone="success"/>}<Card><Toggle label="开发者功能" checked={settings.enabled} onChange={(enabled) => { updateDeveloperSettings({ enabled }); setService(null); }}/><p className="muted">{settings.enabled ? "不会经过 Vercel 或默认后端；浏览器会直接访问你配置的第三方服务，密钥可在本机网络请求中看到。" : "关闭时通过现有线上后端服务访问。"}</p><button className="button secondary full" onClick={checkService} disabled={checking}>{checking ? "正在检测..." : settings.enabled ? "检查浏览器直连配置" : "检测线上代理连接"}</button>{service && <Status message={service.message} tone={service.proxyReachable ? "success" : "error"}/>}</Card><fieldset disabled={!settings.enabled}><SettingsGroup title="AI 设置"><label className="field-label">AI 来源<select value={settings.aiProvider} onChange={(event) => updateDeveloperSettings({ aiProvider: event.target.value as DeveloperSettings["aiProvider"] })}><option value="deepseek">DeepSeek</option><option value="custom">自定义 AI</option></select></label><TextField label="AI Base URL" value={settings.aiBaseUrl} onChange={(aiBaseUrl) => updateDeveloperSettings({ aiBaseUrl })}/><TextField label="AI API Key" type="password" value={settings.aiApiKey} onChange={(aiApiKey) => updateDeveloperSettings({ aiApiKey })}/><TextField label="模型" value={settings.aiModel} onChange={(aiModel) => updateDeveloperSettings({ aiModel })}/></SettingsGroup><SettingsGroup title="地图 API 设置"><TextField label="高德 Web Key" type="password" value={settings.amapWebKey} onChange={(amapWebKey) => updateDeveloperSettings({ amapWebKey })}/></SettingsGroup><SettingsGroup title="菜谱 API 设置"><label className="field-label">菜谱数据源<select value={settings.recipeApiSource} onChange={(event) => updateDeveloperSettings({ recipeApiSource: event.target.value as DeveloperSettings["recipeApiSource"] })}><option value="wanwei">万维易源</option><option value="mxnzp">mxnzp</option><option value="custom">自定义</option></select></label><TextField label="接口地址（留空使用预设）" value={settings.recipeApiBaseUrl} onChange={(recipeApiBaseUrl) => updateDeveloperSettings({ recipeApiBaseUrl })}/>{settings.recipeApiSource === "wanwei" ? <><TextField label="万维易源 AppID" value={settings.recipeApiAppId} onChange={(recipeApiAppId) => updateDeveloperSettings({ recipeApiAppId })}/><TextField label="万维易源 AppKey" type="password" value={settings.wanweiRecipeAppKey} onChange={(wanweiRecipeAppKey) => updateDeveloperSettings({ wanweiRecipeAppKey })}/></> : <><TextField label="app_id" type="password" value={settings.recipeApiAppId} onChange={(recipeApiAppId) => updateDeveloperSettings({ recipeApiAppId })}/><TextField label="app_secret" type="password" value={settings.recipeApiSecret} onChange={(recipeApiSecret) => updateDeveloperSettings({ recipeApiSecret })}/></>}<label className="field-label">每页数量（1-50）<input type="number" min={1} max={50} value={settings.recipePageSize} onChange={(event) => updateDeveloperSettings({ recipePageSize: Math.min(50, Math.max(1, Number(event.target.value))) })}/></label></SettingsGroup><SettingsGroup title="最大等待时长"><label className="field-label">秒（10-300）<input type="number" min={10} max={300} value={settings.maxWaitSeconds} onChange={(event) => updateDeveloperSettings({ maxWaitSeconds: Math.min(300, Math.max(10, Number(event.target.value))) })}/></label></SettingsGroup></fieldset><p className="build-version">网页版本：{__APP_VERSION__}</p></Page></Layout>;
}

function SettingsGroup({ title, children }: { title: string; children: ReactNode }) { return <Card className="settings-group"><h2>{title}</h2><div className="field-stack">{children}</div></Card>; }
function TextField({ label, value, onChange, type = "text" }: { label: string; value: string; onChange: (value: string) => void; type?: string }) { return <label className="field-label">{label}<input type={type} value={value} autoComplete="off" onChange={(event) => onChange(event.target.value)}/></label>; }
function uniqueById<T extends { id: string }>(items: T[]) { return items.filter((item, index, all) => all.findIndex((candidate) => candidate.id === item.id) === index); }

export default function App() { return <AppProvider><Routes><Route path="/" element={<HomePage/>}/><Route path="/cook" element={<CookPage/>}/><Route path="/nearby" element={<NearbyPage/>}/><Route path="/meal/:id" element={<DetailPage kind="meal"/>}/><Route path="/recipe/:id" element={<DetailPage kind="recipe"/>}/><Route path="/restaurant/:id" element={<DetailPage kind="restaurant"/>}/><Route path="/saved" element={<SavedPage/>}/><Route path="/settings" element={<SettingsPage/>}/><Route path="/settings/developer" element={<DeveloperPage/>}/><Route path="*" element={<Navigate to="/" replace/>}/></Routes></AppProvider>; }
