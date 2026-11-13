export default function AccessLayout({ children }) {
  return (
    <div className="access-page">
      <header className="access-header">
        <img className="access-logo" src="/assets/arise-logo.png" width="200" height="77" alt="Arise Corp. — Logística autónoma y última milla" />
      </header>
      <div className="access-columns">
        <div className="access-workspace">
          {children}
        </div>
        <aside className="access-brand" aria-label="Acerca de Arise Corp.">
          <p className="brand-eyebrow">LOGÍSTICA DE ÚLTIMA MILLA</p>
          <h2 className="brand-heading">Conectamos<br />destinos.<br />Elevamos negocios.</h2>
        </aside>
      </div>
    </div>
  )
}
