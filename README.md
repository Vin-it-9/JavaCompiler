# Java Online Compiler

A high-performance, web-based Java compiler and interpreter built with the Quarkus framework.

## Features

- **Fast Compilation**: Optimized Java compilation with class caching for improved performance
- **Secure Execution**: Sandboxed environment for safe code execution
- **Memory Monitoring**: Track memory usage of executed Java programs
- **Code Analysis**: Detect public classes and main methods automatically
- **Execution Statistics**: Monitor compilation time, execution time, and memory usage
- **Responsive UI**: Web interface for easy code input and result viewing
- **Resource Management**: Configurable memory limits and timeouts

## Getting Started

### Prerequisites

- JDK 17 or later
- Maven 3.8+
- Docker (optional, for containerized deployment)

### Installation

1. Clone the repository:
   ```bash
   git clone https://github.com/Vin-it-9/JavaCompiler.git
   cd JavaCompiler
   ```

2. Build the project:
   ```bash
   ./mvnw package
   ```

3. Run the application:
   ```bash
   java -jar target/quarkus-app/quarkus-run.jar
   ```

4. Alternatively, for development mode with hot reload:
   ```bash
   ./mvnw quarkus:dev
   ```

### Docker Deployment

```bash
docker build -f src/main/docker/Dockerfile.jvm -t java-compiler .
docker run -i --rm -p 8080:8080 java-compiler
```

## Usage

1. Access the web interface at `http://localhost:8080`
2. Enter Java code in the editor
3. Click "Compile & Run"
4. View compilation results, execution output, and performance metrics

### API Usage

```bash
curl -X POST "http://localhost:8080/api/compile" \
  -H "Content-Type: application/json" \
  -d '{"sourceCode":"public class Hello { public static void main(String[] args) { System.out.println(\"Hello World\"); }}"}'
```

## Configuration

The compiler's behavior can be configured by modifying the following parameters in `application.properties` or through environment variables:

| Parameter | Description | Default |
|-----------|-------------|---------|
| `compiler.max-memory-mb` | Maximum memory allocated to JVM | 2048 |
| `compiler.stack-size-kb` | Stack size for executing programs | 4096 |
| `compiler.process-timeout-seconds` | Maximum execution time | 15 |
| `compiler.io-buffer-size` | Buffer size for I/O operations | 262144 |

## Technical Details

- **Backend**: Quarkus framework with CDI and RESTEasy
- **Frontend**: HTML, CSS, JavaScript (possibly with a framework)
- **Execution**: Controlled Java process execution with resource limits
- **Memory Management**: JVM memory monitoring through specialized helper class
- **Caching**: Compiled classes are cached for performance
- **Error Handling**: Detailed compilation and runtime error reporting

## Security

The compiler implements several security measures:
- Sandboxed execution environment
- Resource limits (memory, execution time)
- Input validation and sanitization
- Protection against malicious code

