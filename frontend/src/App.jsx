import React, { useState, useEffect } from 'react';
import './App.css';

const API_URL = "http://localhost:8080/api";

export default function App() {
  const [libros, setLibros] = useState([]);
  const [alumnos, setAlumnos] = useState([]);
  const [logs, setLogs] = useState([]);
  const [isOnline, setIsOnline] = useState(navigator.onLine);
  const [deferredPrompt, setDeferredPrompt] = useState(null);

  // Formulario Préstamo
  const [idAlumno, setIdAlumno] = useState('');
  const [idLibro, setIdLibro] = useState('');

  // Formulario Nuevo Alumno
  const [nuevoIdAlumno, setNuevoIdAlumno] = useState('');
  const [nuevoNombreAlumno, setNuevoNombreAlumno] = useState('');
  const [nuevaUniversidad, setNuevaUniversidad] = useState('');

  // Formulario Nuevo Libro
  const [nuevoIdLibro, setNuevoIdLibro] = useState('');
  const [nuevoTituloLibro, setNuevoTituloLibro] = useState('');

  const registrarLog = (mensaje, tipo = "INFO") => {
    const timestamp = new Date().toLocaleTimeString();
    setLogs(prev => [`[${timestamp}] [${tipo}] ${mensaje}`, ...prev]);
  };

  const cargarDatos = async () => {
    try {
      const resL = await fetch(`${API_URL}/libros`);
      const dataL = await resL.json();
      setLibros(dataL);
      localStorage.setItem('cache_libros', JSON.stringify(dataL));

      const resA = await fetch(`${API_URL}/alumnos`);
      const dataA = await resA.json();
      setAlumnos(dataA);
      localStorage.setItem('cache_alumnos', JSON.stringify(dataA));
    } catch (err) {
      const cacheL = JSON.parse(localStorage.getItem('cache_libros') || '[]');
      const cacheA = JSON.parse(localStorage.getItem('cache_alumnos') || '[]');
      setLibros(cacheL);
      setAlumnos(cacheA);
      registrarLog("Modo Offline: Cargando catálogos desde almacenamiento local (PWA).", "SISTEMA");
    }
  };

  useEffect(() => {
    cargarDatos();
    registrarLog("PWA iniciada. Service Worker y Orquestador listos.", "SISTEMA");

    const handleOnline = () => {
      setIsOnline(true);
      registrarLog("Conexión restablecida. Sincronizando con el Orquestador...", "EXITO");
      cargarDatos();
    };
    const handleOffline = () => {
      setIsOnline(false);
      registrarLog("Sin conexión a red. Operando en modo caché local.", "ERROR");
    };
    const handleBeforeInstall = (e) => {
      e.preventDefault();
      setDeferredPrompt(e);
    };

    window.addEventListener('online', handleOnline);
    window.addEventListener('offline', handleOffline);
    window.addEventListener('beforeinstallprompt', handleBeforeInstall);

    return () => {
      window.removeEventListener('online', handleOnline);
      window.removeEventListener('offline', handleOffline);
      window.removeEventListener('beforeinstallprompt', handleBeforeInstall);
    };
  }, []);

  const instalarPWA = async () => {
    if (!deferredPrompt) return;
    deferredPrompt.prompt();
    const { outcome } = await deferredPrompt.userChoice;
    if (outcome === 'accepted') {
      registrarLog("Aplicación PWA instalada en el sistema operativo.", "EXITO");
    }
    setDeferredPrompt(null);
  };

  const handlePrestar = async (e) => {
    e.preventDefault();
    if (!isOnline) {
      registrarLog("No se pueden registrar préstamos en modo Offline.", "ERROR");
      return;
    }
    try {
      const res = await fetch(`${API_URL}/prestamos`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          idAlumno: parseInt(idAlumno),
          idLibro: parseInt(idLibro)
        })
      });

      const data = await res.json();
      if (!res.ok) {
        registrarLog(`Fallo en préstamo: ${data.error}`, "ERROR");
      } else {
        registrarLog(`Préstamo registrado: Alumno ${data.alumno} | Libro ID ${data.idLibro}`, "EXITO");
        setIdAlumno('');
        setIdLibro('');
        cargarDatos();
      }
    } catch (err) {
      registrarLog("Error de red al procesar la solicitud de préstamo.", "ERROR");
    }
  };

  const handleRegistrarAlumno = async (e) => {
    e.preventDefault();
    if (!isOnline) return;
    try {
      const res = await fetch(`${API_URL}/alumnos`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: parseInt(nuevoIdAlumno),
          nombre: nuevoNombreAlumno,
          universidad: nuevaUniversidad
        })
      });

      if (res.ok) {
        registrarLog(`Alumno registrado: ${nuevoNombreAlumno} (ID: ${nuevoIdAlumno})`, "EXITO");
        setNuevoIdAlumno('');
        setNuevoNombreAlumno('');
        setNuevaUniversidad('');
        cargarDatos();
      } else {
        registrarLog("No se pudo registrar al alumno.", "ERROR");
      }
    } catch (err) {
      registrarLog("Error de red al registrar alumno.", "ERROR");
    }
  };

  const handleRegistrarLibro = async (e) => {
    e.preventDefault();
    if (!isOnline) return;
    try {
      const res = await fetch(`${API_URL}/libros`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          id: parseInt(nuevoIdLibro),
          titulo: nuevoTituloLibro
        })
      });

      if (res.ok) {
        registrarLog(`Libro agregado al catálogo: ${nuevoTituloLibro} (ID: ${nuevoIdLibro})`, "EXITO");
        setNuevoIdLibro('');
        setNuevoTituloLibro('');
        cargarDatos();
      } else {
        registrarLog("No se pudo agregar el libro al catálogo.", "ERROR");
      }
    } catch (err) {
      registrarLog("Error de red al registrar libro.", "ERROR");
    }
  };

  const handleLiberar = async (idLibroALiberar) => {
    if (!isOnline) return;
    try {
      const res = await fetch(`${API_URL}/libros/${idLibroALiberar}/liberar`, { method: "PUT" });
      if (res.ok) {
        registrarLog(`Devolución confirmada para el libro con ID ${idLibroALiberar}`, "EXITO");
        cargarDatos();
      } else {
        registrarLog("No se pudo procesar la devolución del libro.", "ERROR");
      }
    } catch (err) {
      registrarLog("Error de red al intentar devolver el libro.", "ERROR");
    }
  };

  return (
    <div className="container">
      <header className="main-header">
        <h1>Sistema de Gestión de Biblioteca Distribuidora (PWA)</h1>
        <p>
          Arquitectura de Microservicios REST / Java HTTP Server &bull;{' '}
          <span className={isOnline ? "status-tag status-available" : "status-tag status-borrowed"}>
            {isOnline ? "EN LÍNEA" : "MODO OFFLINE (CACHÉ)"}
          </span>
        </p>
        {deferredPrompt && (
          <button onClick={instalarPWA} className="btn btn-primary" style={{ marginTop: '10px' }}>
            Instalar Aplicación (PWA)
          </button>
        )}
      </header>

      {/* Control de Transacciones */}
      <section className="panel-section">
        <h2>Gestión de Préstamos</h2>
        <form onSubmit={handlePrestar} className="form-row">
          <div className="field-group">
            <label>ID Alumno</label>
            <input type="number" placeholder="Ej. 101" value={idAlumno} onChange={(e) => setIdAlumno(e.target.value)} required />
          </div>
          <div className="field-group">
            <label>ID Libro</label>
            <input type="number" placeholder="Ej. 1" value={idLibro} onChange={(e) => setIdLibro(e.target.value)} required />
          </div>
          <button type="submit" className="btn btn-primary" disabled={!isOnline}>Registrar Préstamo</button>
        </form>
      </section>

      {/* Tablas Principales */}
      <div className="columns-grid">
        <section className="panel-section">
          <h2>Catálogo de Libros</h2>
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Título</th>
                <th>Estado</th>
                <th>Acción</th>
              </tr>
            </thead>
            <tbody>
              {libros.map(l => (
                <tr key={l.id}>
                  <td>{l.id}</td>
                  <td>{l.titulo}</td>
                  <td>
                    <span className={l.disponible ? "status-tag status-available" : "status-tag status-borrowed"}>
                      {l.disponible ? "Disponible" : "Prestado"}
                    </span>
                  </td>
                  <td>
                    {!l.disponible ? (
                      <button className="btn btn-small btn-danger" onClick={() => handleLiberar(l.id)} disabled={!isOnline}>
                        Devolver
                      </button>
                    ) : (
                      <span className="text-muted">—</span>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="form-subbox">
            <h3>Alta de Libro</h3>
            <form onSubmit={handleRegistrarLibro} className="form-row">
              <input type="number" placeholder="ID" value={nuevoIdLibro} onChange={(e) => setNuevoIdLibro(e.target.value)} required />
              <input type="text" placeholder="Título" value={nuevoTituloLibro} onChange={(e) => setNuevoTituloLibro(e.target.value)} required />
              <button type="submit" className="btn btn-secondary" disabled={!isOnline}>Agregar</button>
            </form>
          </div>
        </section>

        <section className="panel-section">
          <h2>Padrón de Alumnos</h2>
          <table className="data-table">
            <thead>
              <tr>
                <th>ID</th>
                <th>Nombre</th>
                <th>Institución</th>
              </tr>
            </thead>
            <tbody>
              {alumnos.map(a => (
                <tr key={a.id}>
                  <td>{a.id}</td>
                  <td>{a.nombre}</td>
                  <td>{a.universidad}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="form-subbox">
            <h3>Alta de Alumno</h3>
            <form onSubmit={handleRegistrarAlumno} className="form-row">
              <input type="number" placeholder="ID" value={nuevoIdAlumno} onChange={(e) => setNuevoIdAlumno(e.target.value)} required />
              <input type="text" placeholder="Nombre" value={nuevoNombreAlumno} onChange={(e) => setNuevoNombreAlumno(e.target.value)} required />
              <input type="text" placeholder="Universidad" value={nuevaUniversidad} onChange={(e) => setNuevaUniversidad(e.target.value)} required />
              <button type="submit" className="btn btn-secondary" disabled={!isOnline}>Registrar</button>
            </form>
          </div>
        </section>
      </div>

      {/* Ventana de Registro de Actividad */}
      <section className="panel-section">
        <h2>Registro de Actividad del Sistema</h2>
        <div className="terminal-window">
          {logs.length === 0 ? (
            <div className="log-line">Sin actividad registrada.</div>
          ) : (
            logs.map((log, index) => (
              <div key={index} className={`log-line ${log.includes("[ERROR]") ? "log-error" : log.includes("[EXITO]") ? "log-success" : ""}`}>
                {log}
              </div>
            ))
          )}
        </div>
      </section>
    </div>
  );
}