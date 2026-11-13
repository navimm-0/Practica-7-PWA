import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;

public class ServidorLibros {

    static class Libro {
        int id;
        String titulo;
        boolean disponible;

        public Libro(int id, String titulo, boolean disponible) {
            this.id = id;
            this.titulo = titulo;
            this.disponible = disponible;
        }
    }

    private static final List<Libro> librosDB = Collections.synchronizedList(new ArrayList<>(Arrays.asList(
        new Libro(1, "Redes de Computadoras - Tanenbaum", true),
        new Libro(2, "Sistemas Operativos Modernos - Tanenbaum", true)
    )));

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(6001), 0);

        server.createContext("/libros", new HttpHandler() {
            @Override
            public void handle(HttpExchange exchange) {
                configurarCORS(exchange);
                String method = exchange.getRequestMethod();
                String path = exchange.getRequestURI().getPath();

                if ("GET".equalsIgnoreCase(method)) {
                    StringBuilder json = new StringBuilder("[");
                    synchronized (librosDB) {
                        for (int i = 0; i < librosDB.size(); i++) {
                            Libro l = librosDB.get(i);
                            json.append(String.format("{\"id\":%d,\"titulo\":\"%s\",\"disponible\":%b}", l.id, l.titulo, l.disponible));
                            if (i < librosDB.size() - 1) json.append(",");
                        }
                    }
                    json.append("]");
                    enviarRespuesta(exchange, 200, json.toString());

                } else if ("POST".equalsIgnoreCase(method)) {
                    try {
                        InputStream is = exchange.getRequestBody();
                        String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                        int id = Integer.parseInt(extraerValorJson(body, "id"));
                        String titulo = extraerValorStringJson(body, "titulo");

                        synchronized (librosDB) {
                            librosDB.add(new Libro(id, titulo, true));
                        }
                        enviarRespuesta(exchange, 201, "{\"mensaje\":\"Libro agregado exitosamente\"}");
                    } catch (Exception e) {
                        enviarRespuesta(exchange, 400, "{\"error\":\"Datos de libro inválidos\"}");
                    }

                } else if ("PUT".equalsIgnoreCase(method) && path.endsWith("/reservar")) {
                    String[] parts = path.split("/");
                    int idLibro = Integer.parseInt(parts[2]);

                    synchronized (librosDB) {
                        Optional<Libro> libOpt = librosDB.stream().filter(l -> l.id == idLibro).findFirst();
                        if (libOpt.isEmpty()) {
                            enviarRespuesta(exchange, 404, "{\"error\":\"Libro no encontrado\"}");
                            return;
                        }
                        Libro libro = libOpt.get();
                        if (!libro.disponible) {
                            enviarRespuesta(exchange, 400, "{\"error\":\"El libro ya se encuentra prestado\"}");
                            return;
                        }
                        libro.disponible = false;
                        enviarRespuesta(exchange, 200, "{\"mensaje\":\"Libro reservado con éxito\"}");
                    }

                } else if ("PUT".equalsIgnoreCase(method) && path.endsWith("/liberar")) {
                    String[] parts = path.split("/");
                    int idLibro = Integer.parseInt(parts[2]);

                    synchronized (librosDB) {
                        Optional<Libro> libOpt = librosDB.stream().filter(l -> l.id == idLibro).findFirst();
                        if (libOpt.isEmpty()) {
                            enviarRespuesta(exchange, 404, "{\"error\":\"Libro no encontrado\"}");
                            return;
                        }
                        Libro libro = libOpt.get();
                        libro.disponible = true;
                        enviarRespuesta(exchange, 200, "{\"mensaje\":\"Libro devuelto con éxito\"}");
                    }

                } else if ("OPTIONS".equalsIgnoreCase(method)) {
                    enviarRespuesta(exchange, 204, "");
                }
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("[LIBROS] Microservicio REST activo en http://localhost:6001");
    }

    private static void configurarCORS(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, PUT, OPTIONS");
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