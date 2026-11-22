# CS323 Project 5 — local and OJ builds. Requires ANTLR4 complete jar and JDK 17+.
ANTLR_JAR ?= /usr/local/share/antlr/antlr.jar
JAVA ?= java
JAVAC ?= javac
JAR ?= jar

# Each Java tool starts a separate JVM inside the existing Hydro compile-memory sandbox.
# -Xmx limits ONLY the Java heap; Metaspace, compressed class space, code cache,
# native memory, and thread stacks need additional space.
# Keep these settings overridable locally, without changing the global cs323project language.
ANTLR_JVM_OPTS ?= -Xms16m -Xmx128m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:MaxMetaspaceSize=96m -XX:CompressedClassSpaceSize=32m -XX:ReservedCodeCacheSize=32m -Xss512k
JAVAC_JVM_OPTS ?= -J-Xms16m -J-Xmx160m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=1 -J-XX:MaxMetaspaceSize=96m -J-XX:CompressedClassSpaceSize=32m -J-XX:ReservedCodeCacheSize=32m -J-Xss512k
JAR_JVM_OPTS ?= -J-Xms16m -J-Xmx64m -J-XX:+UseSerialGC -J-XX:ActiveProcessorCount=1 -J-XX:MaxMetaspaceSize=64m -J-XX:CompressedClassSpaceSize=24m -J-XX:ReservedCodeCacheSize=24m -J-Xss512k
SOURCE ?= examples/hello.spl
STDIN ?= examples/hello.stdin

.PHONY: help generate build clean test test-public test-all handin ir run check-data testdata package verify
.DEFAULT_GOAL := help

help:
	@echo 'CS323 Project 5 commands:'
	@echo '  make build           Regenerate ANTLR Lexer/Parser/Visitor and compile Java'
	@echo '  make test            Run simple examples + public official tests'
	@echo '  make test-public     Run public official tests (30 runs)'
	@echo '  make ir SOURCE=x.spl Print generated LLVM IR'
	@echo '  make run SOURCE=x.spl STDIN=y.txt   Compile and execute one SPL program'
	@echo '  make handin          Create submission.zip (Hydro cs323project) (student-editable files only)'
	@echo '  make check-data      Validate readable testdata framing and testcase layout'
	@echo '  make clean           Remove compiled/generated artifacts'
	@echo 'Teacher only: make testdata, make test-all, make package, make verify'

# Always regenerate: student is allowed to change labels / parser contexts.
generate:
	@test -f "$(ANTLR_JAR)" || { echo 'ERROR: ANTLR_JAR not found: $(ANTLR_JAR)' >&2; exit 2; }
	rm -rf target/generated target/classes
	mkdir -p target/generated target/classes
	$(JAVA) $(ANTLR_JVM_OPTS) -jar "$(ANTLR_JAR)" -Dlanguage=Java -package generated -o target/generated SplLexer.g4
	$(JAVA) $(ANTLR_JVM_OPTS) -jar "$(ANTLR_JAR)" -Dlanguage=Java -visitor -no-listener -package generated -lib target/generated -o target/generated Splc.g4

build: generate
	$(JAVAC) $(JAVAC_JVM_OPTS) --release 17 -encoding UTF-8 -cp "$(ANTLR_JAR)" -d target/classes $$(find src/main/java target/generated -name '*.java' -type f)
	$(JAR) $(JAR_JVM_OPTS) --create --file target/project.jar --main-class Main -C target/classes . -C . runtime/runtime.c
	@echo '[BUILD] target/project.jar'

clean:
	rm -rf target project.jar project5.jar

check-data:
	python3 scripts/protocol_selftest.py
	python3 scripts/check_testdata.py

test: build check-data
	ANTLR_JAR="$(ANTLR_JAR)" python3 scripts/run_suite.py --scope public --examples

test-public: build check-data
	ANTLR_JAR="$(ANTLR_JAR)" python3 scripts/run_suite.py --scope public

test-all: build check-data
	ANTLR_JAR="$(ANTLR_JAR)" python3 scripts/run_suite.py --scope all --examples

ir: build
	$(JAVA) -Xmx128m -cp "target/project.jar:$(ANTLR_JAR)" Main --emit-ir "$(SOURCE)"

run: build
	$(JAVA) -Xmx128m -cp "target/project.jar:$(ANTLR_JAR)" Main --run-spl "$(SOURCE)" < "$(STDIN)"

handin:
	bash scripts/handin.sh

testdata:
	python3 scripts/generate_testdata.py
	python3 scripts/check_testdata.py

package: check-data
	python3 scripts/package_release.py

verify: test-all package
	python3 scripts/check_release.py
