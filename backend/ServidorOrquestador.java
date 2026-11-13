import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ServidorOrquestador {

    private static final HttpClient client = HttpClient.newHttpClient();

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Rutas Alumnos
        server.createContext("/api/alumnos", exchange -> {
            configurarCORS(exchange);
            String method = exchange.getRequestMethod();
            if ("GET".equalsIgnoreCase(method)) {
                String resp = consumirHttp("http://localhost:8001/alumnos", "GET", null);
                enviarRespuesta(exchange, 200, resp);
            } else if ("POST".equalsIgnoreCase(method)) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    HttpResponse<String> res = consumirHttpResponse("http://localhost:8001/alumnos", "POST", body);
                    enviarRespuesta(exchange, res.statusCode(), res.body());
                } catch (Exception e) {
                    enviarRespuesta(exchange, 400, "{\"error\":\"Error al crear alumno\"}");
                }
            } else {
                enviarRespuesta(exchange, 204, "");
            }
        });

        // Rutas Libros
        server.createContext("/api/libros", exchange -> {
            configurarCORS(exchange);
            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if ("GET".equalsIgnoreCase(method)) {
                String resp = consumirHttp("http://localhost:6001/libros", "GET", null);
                enviarRespuesta(exchange, 200, resp);
            } else if ("POST".equalsIgnoreCase(method)) {
                try {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    HttpResponse<String> res = consumirHttpResponse("http://localhost:6001/libros", "POST", body);
                    enviarRespuesta(exchange, res.statusCode(), res.body());
                } catch (Exception e) {
                    enviarRespuesta(exchange, 400, "{\"error\":\"Error al agregar libro\"}");
                }
            } else if ("PUT".equalsIgnoreCase(method) && path.endsWith("/liberar")) {
                try {
                    String[] parts = path.split("/");
                    int idLibro = Integer.parseInt(parts[3]);
                    HttpResponse<String> res = consumirHttpResponse("http://localhost:6001/libros/" + idLibro + "/liberar", "PUT", null);
                    enviarRespuesta(exchange, res.statusCode(), res.body());
                } catch (Exception e) {
                    enviarRespuesta(exchange, 400, "{\"error\":\"Error al liberar el libro\"}");
                }
            } else {
                enviarRespuesta(exchange, 204, "");
            }
        });

        // Rutas Préstamos
        server.createContext("/api/prestamos", exchange -> {
            configurarCORS(exchange);
            if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                try {
                    InputStream is = exchange.getRequestBody();
                    String body = new String(is.readAllBytes(), StandardCharsets.UTF_8);

                    int idAlumno = Integer.parseInt(extraerValorJson(body, "idAlumno"));
                    int idLibro = Integer.parseInt(extraerValorJson(body, "idLibro"));

                    HttpResponse<String> resAl = consumirHttpResponse("http://localhost:8001/alumnos/" + idAlumno, "GET", null);
                    if (resAl.statusCode() == 404) {
                        enviarRespuesta(exchange, 404, "{\"error\":\"Alumno no encontrado en el Padrón\"}");
                        return;
                    }

                    HttpResponse<String> resLib = consumirHttpResponse("http://localhost:6001/libros/" + idLibro + "/reservar", "PUT", null);
                    if (resLib.statusCode() == 400) {
                        enviarRespuesta(exchange, 400, "{\"error\":\"El libro seleccionado ya se encuentra prestado\"}");
                        return;
                    }
                    if (resLib.statusCode() == 404) {
                        enviarRespuesta(exchange, 404, "{\"error\":\"El libro seleccionado no existe\"}");
                        return;
                    }

                    String nombreAlumno = extraerValorStringJson(resAl.body(), "nombre");
                    String fecha = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    String resJson = String.format("{\"mensaje\":\"Préstamo registrado exitosamente\",\"alumno\":\"%s\",\"idLibro\":%d,\"fecha\":\"%s\"}",
                            nombreAlumno, idLibro, fecha);

                    enviarRespuesta(exchange, 201, resJson);

                } catch (Exception e) {
                    enviarRespuesta(exchange, 400, "{\"error\":\"Error en transacción de microservicios\"}");
                }
            } else {
                enviarRespuesta(exchange, 204, "");
            }
        });

        server.setExecutor(null);
        server.start();
        System.out.println("[ORQUESTADOR] Microservicio REST activo en http://localhost:8080");
    }

    private static String consumirHttp(String url, String metodo, String body) {
        try {
            return consumirHttpResponse(url, metodo, body).body();
        } catch (Exception e) {
            return "[]";
        }
    }

    private static HttpResponse<String> consumirHttpResponse(String url, String metodo, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder().uri(URI.create(url));
        if ("PUT".equalsIgnoreCase(metodo)) {
            builder.PUT(body != null ? HttpRequest.BodyPublishers.ofString(body) : HttpRequest.BodyPublishers.noBody());
        } else if ("POST".equalsIgnoreCase(metodo)) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body != null ? body : ""));
        } else {
            builder.GET();
        }
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
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
        if (inicio == -1) return "Estudiante";
        inicio += patron.length();
        int fin = json.indexOf("\"", inicio);
        return json.substring(inicio, fin);
    }
}