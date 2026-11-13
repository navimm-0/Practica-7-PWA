# Biblioteca federada

## Requisitos

- Java 25 o posterior.
- Maven 3.9 o posterior, disponible en `PATH` (recomendado para compilar con Java 25).
- Node.js 20.19+ (rama 20) o 22.12+ y npm para ejecutar el frontend (Vite 8).

## Backend

Desde PowerShell, en la carpeta `backend`:

```powershell
.\run_backend.ps1
```

El script inicia y comprueba los tres servicios:

- Alumnos: `http://localhost:8001/alumnos`
- Libros: `http://localhost:6001/libros`
- Orquestador: `http://localhost:8080/api`

En Linux o macOS se puede usar `./run_backend.sh` desde esa misma carpeta.
Detén los servicios con `Ctrl+C`.

## Frontend

En otra terminal:

```powershell
cd frontend
npm install
npm run dev
```

Abre la dirección que Vite muestre en la terminal. El frontend se comunica con el
orquestador en `http://localhost:8080/api`.
