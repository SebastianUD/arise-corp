import { useId } from 'react'
import { NavLink } from 'react-router-dom'

export default function GrupoMenu({ nombre, icono, opciones, abierto, activo, onToggle }) {
  const id = useId()
  return <div className="nav-accordion">
    <button type="button" id={id + '-control'} className={'nav-item nav-group' + (activo ? ' selected' : '')}
      aria-expanded={abierto} aria-controls={id} onClick={onToggle}>
      <img src={icono} alt="" />
      <span>{nombre}</span>
      <svg className="nav-chevron" width="16" height="16" viewBox="0 0 24 24" fill="none" aria-hidden="true">
        <path d="m6 9 6 6 6-6" stroke="currentColor" strokeWidth="1.8" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </button>
    <div id={id} className="nav-sub" hidden={!abierto} aria-labelledby={id + '-control'}>
      {opciones.map(({ texto, ruta }) => <NavLink key={ruta} to={ruta}>{texto}</NavLink>)}
    </div>
  </div>
}
