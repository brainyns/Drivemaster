import { useEffect } from "react";

/**
 * Hace aparecer los bloques marcados con data-reveal cuando entran en pantalla (solo opacidad y
 * transform, ver landing.css). Usa IntersectionObserver, nunca un listener de scroll. Sin soporte,
 * o con "reducir movimiento", los bloques se muestran de una vez.
 *
 * activo: si la vista con bloques está montada. clave: algo que cambia cuando llegan bloques nuevos
 * (por ejemplo, las tiendas que se cargan después).
 */
export default function useRevelar(activo, clave) {
  useEffect(() => {
    if (!activo) return undefined;
    const nodos = [...document.querySelectorAll("[data-reveal]:not(.dm-visto)")];
    if (!nodos.length) return undefined;
    const sinMovimiento = window.matchMedia?.("(prefers-reduced-motion: reduce)").matches;
    if (sinMovimiento || typeof IntersectionObserver === "undefined") {
      nodos.forEach((n) => n.classList.add("dm-visto"));
      return undefined;
    }
    const observador = new IntersectionObserver((entradas) => {
      for (const e of entradas) {
        if (e.isIntersecting) {
          e.target.classList.add("dm-visto");
          observador.unobserve(e.target);
        }
      }
    }, { threshold: 0.12, rootMargin: "0px 0px -6% 0px" });
    nodos.forEach((n) => observador.observe(n));
    return () => observador.disconnect();
  }, [activo, clave]);
}
