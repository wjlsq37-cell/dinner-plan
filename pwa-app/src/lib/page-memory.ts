interface PageMemoryBase {
  scrollY: number;
  hasRenderedResults: boolean;
}

const pageMemory = new Map<string, PageMemoryBase>();

export function readPageMemory<T extends PageMemoryBase>(key: string): T | undefined {
  return pageMemory.get(key) as T | undefined;
}

export function writePageMemory<T extends PageMemoryBase>(key: string, value: T): void {
  const { query: _query, ...safeValue } = value as T & { query?: unknown };
  pageMemory.set(key, safeValue as PageMemoryBase);
}

export function restorePageScroll(key: string): void {
  const value = pageMemory.get(key);
  if (!value?.scrollY) return;
  window.requestAnimationFrame(() => window.scrollTo({ top: value.scrollY, behavior: "auto" }));
}

export function clearPageMemory(): void {
  pageMemory.clear();
}
