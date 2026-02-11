# ==============================================================================
# Heroku Procfile - JVM Mode (Backup/Testing)
# ==============================================================================
#
# This Procfile is used for JVM-based deployment (not native).
# For production, use native image deployment via GitHub Actions instead.
#
# Usage:
# 1. Deploy to Heroku: git push heroku main
# 2. Heroku will automatically detect this Procfile
# 3. The web process will start with optimized JVM flags
#
# To deploy native image (recommended):
# - Use GitHub Actions workflow (.github/workflows/deploy-heroku.yml)
# - This provides better performance and lower memory usage
#
# ==============================================================================

web: java \
  -Xms128m \
  -Xmx256m \
  -Xss1m \
  -XX:+UseSerialGC \
  -XX:TieredStopAtLevel=1 \
  -XX:+DisableAttachMechanism \
  -XX:MaxMetaspaceSize=64m \
  -Dquarkus.http.host=0.0.0.0 \
  -Dquarkus.http.port=$PORT \
  -Djava.awt.headless=true \
  --enable-preview \
  -jar target/quarkus-app/quarkus-run.jar
