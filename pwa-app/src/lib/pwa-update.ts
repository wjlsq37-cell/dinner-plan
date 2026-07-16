export type PwaUpdater = (reloadPage?: boolean) => Promise<void>;

let pendingUpdater: PwaUpdater | null = null;
const listeners = new Set<(updater: PwaUpdater) => void>();

export function publishPwaUpdate(updater: PwaUpdater) {
  pendingUpdater = updater;
  listeners.forEach((listener) => listener(updater));
}

export function subscribePwaUpdate(listener: (updater: PwaUpdater) => void) {
  listeners.add(listener);
  if (pendingUpdater) queueMicrotask(() => pendingUpdater && listener(pendingUpdater));
  return () => listeners.delete(listener);
}

export function dismissPwaUpdate() {
  pendingUpdater = null;
}
