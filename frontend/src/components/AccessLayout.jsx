export default function AccessLayout({ children }) {
  return (
    <div className="access-page">
      <header className="access-header">
        <img className="access-logo" src="/assets/arise-logo.png" width="200" height="77" alt="Arise Corp. — Logística autónoma y última milla" />
        <p className="access-header-note">Gestión que mueve tu negocio</p>
      </header>
      <div className="access-columns">
        <div className="access-workspace">
          {children}
        </div>
        <aside className="access-brand" aria-label="Acerca de Arise Corp.">
          <p className="brand-eyebrow">LOGÍSTICA DE ÚLTIMA MILLA</p>
          <h2 className="brand-heading">Conectamos<br />destinos.<br />Elevamos negocios.</h2>
          <p className="brand-description">Personas, entregas y decisiones conectadas en un solo lugar.</p>
          <div className="brand-flight" aria-hidden="true">
            <svg viewBox="0 0 440 240" fill="none">
              <ellipse cx="220" cy="210" rx="126" ry="14" fill="#051f37" opacity=".4" />
              <path d="M24 180C85 70 125 252 218 167S341 62 416 112" stroke="#75cfff" strokeWidth="2" strokeDasharray="5 9" opacity=".5" />
              <g stroke="#b9e8ff" strokeWidth="3" strokeLinejoin="round">
                <path d="m145 78 50 34m100-34-50 34m-100 32 50-32m100 32-50-32" strokeWidth="8" />
                <ellipse cx="134" cy="73" rx="43" ry="12" /><ellipse cx="306" cy="73" rx="43" ry="12" />
                <ellipse cx="134" cy="149" rx="43" ry="12" /><ellipse cx="306" cy="149" rx="43" ry="12" />
                <path d="m192 102 28-12 28 12v22l-28 13-28-13Z" fill="#177cb1" />
                <path d="m206 135-7 23m35-23 7 23M194 158h52" />
                <path d="m207 169 13-6 13 6v22l-13 7-13-7Z" fill="#1265e8" />
                <path d="m207 169 13 7 13-7m-13 7v22" strokeWidth="2" />
              </g>
              <circle cx="24" cy="180" r="5" fill="#8fd6ff" /><circle cx="416" cy="112" r="5" fill="#8fd6ff" />
            </svg>
          </div>
          <ul className="brand-modules" aria-label="Módulos integrados">
            <li><strong>CRM</strong><span>Conecta con tus clientes</span></li>
            <li><strong>ERP</strong><span>Organiza tus recursos</span></li>
            <li><strong>SGA</strong><span>Coordina cada entrega</span></li>
          </ul>
        </aside>
      </div>
    </div>
  )
}
