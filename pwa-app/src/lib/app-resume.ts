let externalNavigationTrigger: HTMLElement | null = null;

export function markExternalNavigation(trigger: HTMLElement): void {
  externalNavigationTrigger = trigger;
  document.documentElement.dataset.externalNavigation = "pending";
}

export function resumeExternalNavigation(): void {
  if (document.documentElement.dataset.externalNavigation !== "pending") return;
  delete document.documentElement.dataset.externalNavigation;
  const trigger = externalNavigationTrigger;
  externalNavigationTrigger = null;
  window.requestAnimationFrame(() => {
    if (!trigger?.isConnected) return;
    trigger.focus({ preventScroll: true });
  });
}
