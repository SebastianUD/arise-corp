const modules = [
  ['01', 'CRM', 'Clientes y contratos'],
  ['02', 'ERP', 'Personas y recursos'],
  ['03', 'SGA', 'Paquetes y trazabilidad'],
]

export default function AccessLayout({ children }) {
  return (
    <div className="access-page">
      <header className="access-header">
        <img className="access-logo" src="/assets/arise-logo.png" width="200" height="77" alt="Arise Corp. — Logística autónoma y última milla" />
        <p className="access-locale"><span>Tema claro</span><span aria-hidden="true"> / </span><span>Español (CO)</span></p>
      </header>
      <div className="access-columns">
        <div className="access-workspace">
          {children}
          <p className="access-footer">MVP · Mockup de acceso · Datos de ejemplo</p>
        </div>
        <aside className="access-brand" aria-label="Acerca de Arise Corp.">
          <p className="brand-eyebrow">LOGÍSTICA DE ÚLTIMA MILLA</p>
          <h2 className="brand-heading">Conectamos<br />destinos.<br />Elevamos negocios.</h2>
          <p className="brand-description">Tu equipo comercial, administrativo y operativo.<br />Una sola plataforma para avanzar juntos.</p>
          <ul className="brand-modules" aria-label="Áreas de la plataforma">
            {modules.map(([number, name, description]) => (
              <li key={name} className="brand-module">
                <span className="module-number">{number}</span>
                <span className="module-name">{name}</span>
                <span className="module-description">{description}</span>
              </li>
            ))}
          </ul>
          <p className="brand-signature">ARISE CORP. / COLOMBIA</p>
        </aside>
      </div>
    </div>
  )
}
