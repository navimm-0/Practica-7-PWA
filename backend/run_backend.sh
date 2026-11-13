#!/bin/bash

echo "==> Compilando Microservicios Java..."
javac --release 25 ServidorAlumnos.java ServidorLibros.java ServidorOrquestador.java || exit 1

echo "==> Iniciando Servidor Alumnos (Puerto 8001)..."
java ServidorAlumnos &
PID1=$!

echo "==> Iniciando Servidor Libros (Puerto 6001)..."
java ServidorLibros &
PID2=$!

sleep 1

echo "==> Iniciando Servidor Orquestador (Puerto 8080)..."
java ServidorOrquestador &
PID3=$!

echo "==>[+] Microservicios activos."
trap "kill $PID1 $PID2 $PID3 2>/dev/null; echo -e '\n==> Servidores detenidos.'; exit" INT
wait