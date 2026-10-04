import { useEffect, useRef } from "react";
import Icon from "./Icon";

/**
 * Cáscara de modal del mockup: bloquea el scroll, atrapa el foco, cierra con
 * Esc o clic en el overlay y devuelve el foco al elemento que lo abrió.
 */
export default function Modal({ title, children, onClose, wide = false }) {
  const ref = useRef(null);

  useEffect(() => {
    const previo = document.activeElement;
    const scrollPrevio = document.body.style.overflow;
    document.body.style.overflow = "hidden";
    ref.current?.focus();

    const onKey = (e) => {
      if (e.key === "Escape") {
        onClose();
        return;
      }
      if (e.key !== "Tab") return;

      const focusables = ref.current?.querySelectorAll(
        'button:not(:disabled), input:not(:disabled), select:not(:disabled), textarea:not(:disabled), a[href]',
      );
      if (!focusables?.length) return;

      const primero = focusables[0];
      const ultimo = focusables[focusables.length - 1];

      if (e.shiftKey && (document.activeElement === primero || document.activeElement === ref.current)) {
        e.preventDefault();
        ultimo.focus();
      } else if (!e.shiftKey && document.activeElement === ultimo) {
        e.preventDefault();
        primero.focus();
      }
    };

    document.addEventListener("keydown", onKey);
    return () => {
      document.body.style.overflow = scrollPrevio;
      document.removeEventListener("keydown", onKey);
      previo?.focus?.();
    };
  }, [onClose]);

  return (
    <div
      className="dm-modal-overlay"
      onMouseDown={(e) => {
        if (e.target === e.currentTarget) onClose();
      }}
    >
      <div
        className={`dm-modal ${wide ? "dm-modal--wide" : ""}`}
        ref={ref}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-label={title}
      >
        <div className="dm-modal-header">
          <span className="dm-eyebrow">DRIVEMASTER / {title}</span>
          <button className="dm-icon-btn" onClick={onClose} aria-label="Cerrar">
            <Icon name="close" />
          </button>
        </div>
        {children}
      </div>
    </div>
  );
}