import { Link } from 'react-router-dom'
import { useAuth } from '../context/AuthContext.jsx'

export default function Inicio() {
  const { session, loading } = useAuth()
  const autenticado = !loading && session && (!session.expires_at || session.expires_at * 1000 > Date.now())

  return <div className="landing-page">
    <header className="landing-header">
      <Link to="/" aria-label="Arise Corp. - Inicio"><img src="/assets/arise-logo.png" alt="Arise Corp." width="200" height="77" /></Link>
      <nav aria-label="Navegación de inicio">
        <a href="#mision">Misión</a>
        <a href="#vision">Visión</a>
        <Link className="button" to={autenticado ? '/panel' : '/login'}>{autenticado ? 'Ir al panel' : 'Iniciar sesión'}</Link>
      </nav>
    </header>

    <section className="landing-hero" aria-labelledby="inicio-titulo">
      <div className="landing-hero-content">
        <p className="landing-eyebrow">INGENIERÍA COLOMBIANA · LOGÍSTICA AUTÓNOMA</p>
        <h1 id="inicio-titulo">Conectamos destinos.<br /><span>Elevamos negocios.</span></h1>
        <p className="landing-intro">Diseñamos y fabricamos drones autónomos para transformar la entrega de paquetes y acercar empresas, comercios y personas.</p>
        <a className="landing-hero-action" href="#mision">Conoce nuestro propósito</a>
      </div>
      <div className="landing-flight" aria-hidden="true">
        <svg viewBox="0 0 480 400" fill="none">
          <ellipse cx="240" cy="210" rx="205" ry="145" stroke="currentColor" opacity=".2" />
          <ellipse cx="240" cy="210" rx="145" ry="100" stroke="currentColor" opacity=".25" />
          <path d="M50 325C130 330 115 225 240 245S340 140 425 90" stroke="currentColor" strokeWidth="2" strokeDasharray="6 9" opacity=".65" />
          <circle cx="50" cy="325" r="7" fill="currentColor" /><circle cx="425" cy="90" r="7" fill="currentColor" />
          <g stroke="currentColor" strokeWidth="3" strokeLinecap="round" strokeLinejoin="round">
            <path d="m204 170-64-47m136 47 64-47m-136 67-64 43m136-43 64 43" />
            <ellipse cx="132" cy="118" rx="48" ry="15" /><ellipse cx="348" cy="118" rx="48" ry="15" />
            <ellipse cx="132" cy="237" rx="48" ry="15" /><ellipse cx="348" cy="237" rx="48" ry="15" />
            <path d="m204 166 36-16 36 16v30l-36 16-36-16z" fill="#104b6d" />
            <path d="m220 212-9 18m49-18 9 18" />
            <path d="m216 253 24-11 24 11v33l-24 12-24-12zm0 0 24 12 24-12m-24 12v33" />
          </g>
        </svg>
        <p>El futuro de la última milla<br /><strong>empieza con una idea que se eleva.</strong></p>
      </div>
    </section>

    <div className="landing-purpose">
      <div className="landing-section-intro"><p className="landing-eyebrow">NUESTRO PROPÓSITO</p><h2>Una nueva forma de llegar más lejos.</h2><p>Tecnología, seguridad y confianza para impulsar la logística desde Colombia.</p></div>
      <div className="landing-purpose-grid">
        <section id="mision" className="landing-statement" aria-labelledby="mision-titulo">
          <p className="landing-kicker">LO QUE NOS MUEVE</p>
          <h2 id="mision-titulo">Nuestra misión</h2>
          <p>En Arise Corp. diseñamos y fabricamos drones autónomos especializados en la entrega rápida y segura de paquetes, integrando aeronáutica, inteligencia artificial, sistemas de navegación y tecnologías de comunicación para optimizar los procesos de última milla.</p>
          <p>Buscamos ofrecer soluciones logísticas eficientes, confiables y sostenibles que permitan a empresas y comercios mejorar sus tiempos de entrega y llegar a sus clientes de manera innovadora, contribuyendo al desarrollo tecnológico y logístico de Colombia.</p>
        </section>
        <section id="vision" className="landing-statement landing-vision" aria-labelledby="vision-titulo">
          <p className="landing-kicker">NUESTRO HORIZONTE · 2035</p>
          <h2 id="vision-titulo">Nuestra visión</h2>
          <p>Para 2035, Arise Corp. será una empresa referente en Colombia y Latinoamérica en el desarrollo de soluciones de entrega mediante drones autónomos, reconocida por su innovación, seguridad, eficiencia y confiabilidad.</p>
          <p>Aspiramos a transformar la logística de última milla mediante una red de entrega aérea inteligente, convirtiendo la ingeniería colombiana en un referente internacional de la nueva generación de servicios de mensajería y comercio electrónico.</p>
        </section>
      </div>
    </div>
    <footer className="landing-footer"><p>Arise Corp.</p><p>Logística autónoma y última milla · Colombia</p><Link to={autenticado ? '/panel' : '/login'}>Acceso corporativo</Link></footer>
  </div>
}
