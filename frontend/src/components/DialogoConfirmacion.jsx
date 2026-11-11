import { useEffect, useId, useRef } from 'react'

export default function DialogoConfirmacion({ titulo, texto, onConfirmar, onCancelar }) {
  const ref = useRef(null)
  const id = useId()
  useEffect(() => {
    const dialogo = ref.current
    dialogo.showModal()
    return () => dialogo.close()
  }, [])

  return (
    <dialog ref={ref} role="dialog" aria-modal="true" aria-labelledby={id} aria-describedby={id + '-texto'}
      className="m-auto max-w-md rounded-xl border border-slate-700 bg-slate-900 p-6 text-slate-100 backdrop:bg-black/60"
      onCancel={evento => { evento.preventDefault(); onCancelar() }}>
      <h2 id={id} className="text-xl font-semibold">{titulo}</h2>
      <p id={id + '-texto'} className="my-4">{texto}</p>
      <div className="flex justify-end gap-3">
        <button type="button" className="button" autoFocus onClick={onCancelar}>Cancelar</button>
        <button type="button" className="button" onClick={onConfirmar}>Confirmar</button>
      </div>
    </dialog>
  )
}
