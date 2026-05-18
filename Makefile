# OmniMesh — hackathon / local dev shortcuts
# Requires: GNU Make, Node.js (see .nvmrc), JDK 17+, Android SDK for Android targets

.DEFAULT_GOAL := help

NPM ?= npm
WEB_DIR := web
FN_DIR := functions

.PHONY: help setup install-web install-functions dev-web build-web deploy-hosting android-debug clean-web functions-emulators check-node

help:
	@echo "OmniMesh targets:"
	@echo "  make setup              Install web + Cloud Functions npm dependencies"
	@echo "  make dev-web            Start React dashboard (http://localhost:3000)"
	@echo "  make build-web          Production build → web/build (Firebase Hosting)"
	@echo "  make deploy-hosting     Build web + firebase deploy (needs firebase login)"
	@echo "  make android-debug      Build Android debug APK (requires ANDROID_HOME)"
	@echo "  make functions-emulators  Run Functions emulator (needs firebase-tools + functions/.env)"
	@echo "  make check-node         Verify Node major version vs .nvmrc"
	@echo ""
	@echo "First-time: copy env templates (see docs/HACKATHON_SETUP.md)"
	@echo "  cp web/.env.example web/.env"
	@echo "  cp secrets.properties.example secrets.properties"

check-node:
	@node -e "const fs=require('fs');const w=+fs.readFileSync('.nvmrc','utf8').trim().split('.')[0];const m=process.version.slice(1).split('.')[0];if(+m!==w)console.warn('WARN: Node',process.version,'≠ .nvmrc major',w,'— use nvm use');else console.log('OK Node',process.version,'(.nvmrc',w+')');"

install-web:
	cd $(WEB_DIR) && $(NPM) install

install-functions:
	cd $(FN_DIR) && $(NPM) install

setup: check-node install-web install-functions
	@echo ""
	@echo "✓ Dependencies installed. Next:"
	@echo "  1. cp web/.env.example web/.env   (Firebase web config)"
	@echo "  2. cp secrets.properties.example secrets.properties   (Android Gemini key)"
	@echo "  3. Download google-services.json → app/ (Firebase Android app)"
	@echo "  4. make dev-web"

dev-web:
	cd $(WEB_DIR) && $(NPM) start

build-web:
	cd $(WEB_DIR) && $(NPM) run build

deploy-hosting:
	$(NPM) run deploy:hosting

android-debug:
	./gradlew :app:assembleDebug --warning-mode all

clean-web:
	rm -rf $(WEB_DIR)/build $(WEB_DIR)/node_modules

functions-emulators:
	@test -f $(FN_DIR)/.env || (echo "Missing functions/.env — copy from functions/.env.example and set GEMINI_API_KEY"; exit 1)
	bash -lc 'set -a && source "$(FN_DIR)/.env" && set +a && cd "$(FN_DIR)" && $(NPM) run serve'
