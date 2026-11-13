import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ServidorAlumnos {

    static class Alumno {
        int id;
        String nombre;
        String universidad;

        public Alumno(int id, String nombre, String universidad) {
            this.id = id;
            this.nombre = nombre;
            this.universidad = universidad;
        }
    }

    private static final List<Alumno> alumnosDB = Collections.synchronizedList(new ArrayList<>(Arrays.asList(
        new Alumno(101, "Carlos Pérez", "Universidad 1"),
        new Alumno(201, "Ana Gómez", "Universidad 2")
    )));

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8001), 0);

        server.createContext("/alumnos", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                configurarCORS(exchange);
                String method = exchange.getRequestMethod();

                if ("GET".equalsIgnoreCase(method)) {
                    String path = exchange.getRequestURI().getPath();
                    String[] parts = path.split("/");

                    if (parts.length > 2) {
                        int id = Integer.parseInt(parts[2]);
                        Optional<Alumno> alOpt = alumnosDB.stream().filter(a -> a.id == id).findFirst();
                        if (alOpt.isPresent()) {
                            Alumno a = alOpt.get();
                            String json = String.format("{\"id\":%d,\"nombre\":\"%s\",\"universidad\":\"%s\"}", a.id, a.nombre, a.universidad);
                            enviarRespuesta(exchange, 200, json);
                        } else {
                            enviarRespuesta(exchange, 404, "{\"error\":\"Alumno no encontrado\"}");
                        }
                    } else {
                        StringBuilder json = new StringBuilder("[");
                        synchronized (alumnosDB) {
                            for (int i = 0; i < alumnosDB.size(); i++) {
                                Alumno a = alumnosDB.get(i);
                                json.append(String.format("{\"id\":%d,\"nombre\":\"%s\",\"universidad\":\"%s\"}", a.id, a.nombre, a.universidad));
                                if (i < alumnosDB.size() - 1) json.append(",");
                            }
                        }
                        json.append("]");
                        enviarRespuesta(exchange, 200, json.toString());
                    }
                } else if ("POST".equalsIgnoreCase(method)) {
                    try {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                        int id = Integer.parseInt(extraerValorJson(body, "id"));
                        String nombre = extraerValorStringJson(body, "nombre");
                        String universidad = extraerValorStringJson(body, "universidad");

                        synchronized (alumnosDB) {
                            alumnosDB.add(new Alumno(id, nombre, universidad));
                        }
                        enviarRespuesta(exchange, 201, "{\"mensaje\":\"Alumno registrado exitosamente\"}");
                    } catch (Exception e) {
                        enviarRespuesta(exchange, 400, "{\"error\":\"Datos de alumno inválidos\"}");
                    }
                } else if ("OPTIONS".equalsIgnoreCase(method)) {
                    enviarRespuesta(exchange, 204, "");
                }
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("[ALUMNOS] Microservicio REST activo en http://localhost:8001");
    }

    private static void configurarCORS(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type");
    }

    private static void enviarRespuesta(HttpExchange exchange, int statusCode, String respuesta) {
        try {
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            OutputStream os = exchange.getResponseBody();
            os.write(bytes);
            os.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static String extraerValorJson(String json, String clave) {
        String patron = "\"" + clave + "\":";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return "0";
        inicio += patron.length();
        int fin = json.indexOf(",", inicio);
        if (fin == -1) fin = json.indexOf("}", inicio);
        return json.substring(inicio, fin).replaceAll("[^0-9]", "").trim();
    }

    private static String extraerValorStringJson(String json, String clave) {
        String patron = "\"" + clave + "\":\"";
        int inicio = json.indexOf(patron);
        if (inicio == -1) return "";
        inicio += patron.length();
        int fin = json.indexOf("\"", inicio);
        return json.substring(inicio, fin);
    }
}