import { useEffect, useState, type ReactNode } from "react";

const loadedUrls = new Set<string>();

export function CachedImage({ src, alt = "", priority = false, fallback }: { src?: string | null; alt?: string; priority?: boolean; fallback: ReactNode }) {
  const [failed, setFailed] = useState(false);
  const [loaded, setLoaded] = useState(() => Boolean(src && loadedUrls.has(src)));
  useEffect(() => { setFailed(false); setLoaded(Boolean(src && loadedUrls.has(src))); }, [src]);

  if (!src || failed) return <>{fallback}</>;
  return <img
    src={src}
    alt={alt}
    loading={priority ? "eager" : "lazy"}
    fetchPriority={priority ? "high" : "auto"}
    decoding="async"
    className={loaded ? "image-loaded" : "image-loading"}
    onLoad={() => { loadedUrls.add(src); setLoaded(true); }}
    onError={() => setFailed(true)}
  />;
}

export function clearLoadedImages(): void {
  loadedUrls.clear();
}
