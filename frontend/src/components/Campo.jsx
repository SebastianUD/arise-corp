import { Children, cloneElement, useId } from 'react'

export default function Campo({ etiqueta, obligatorio, error, children }) {
  const generado = useId()
  const control = Children.only(children)
  const id = control.props.id ?? generado
  const requerido = obligatorio ?? control.props.required
  const idError = id + '-error'
  const descripcion = [control.props['aria-describedby'], error && idError].filter(Boolean).join(' ')
  return (
    <div className="grid gap-1">
      <label htmlFor={id}>{etiqueta}{requerido && <span aria-hidden="true"> *</span>}</label>
      {cloneElement(control, {
        id, required: requerido, 'aria-invalid': Boolean(error), 'aria-describedby': descripcion || undefined,
      })}
      {error && <p id={idError} className="text-sm text-rose-700">{error}</p>}
    </div>
  )
}
