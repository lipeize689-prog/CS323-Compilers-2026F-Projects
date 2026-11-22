# CS323 Project 5 — SPL → LLVM IR（Student Starter v9）

本项目根据 SPL 源程序生成 LLVM IR，通过 LLVM Verifier、Clang 和固定 Runtime 运行，并按照可执行程序的实际输出评分。**评测不要求生成与教师相同的 IR 文本，也不强制使用指定的 IR Builder。**

## 学生可修改的文件

- `Splc.g4`：可修改 Parser 规则的组织形式、为 Alternative 添加 `#Label`、为运算符添加 `op=`；不得改变 SPL 语言定义、优先级、结合性或 token 规则，保留 `grammar Splc` 与 `program` 入口。
- `src/main/java/impl/**/*.java`：自行实现 `IRGenerator` 和可选辅助类。必须保留 `new IRGenerator().generate(Splc.ProgramContext) -> String` 接口。

`SplLexer.g4`、`Main.java`、Runtime、Makefile、OJ 安装脚本均为固定 Framework；不属于提交内容。推荐使用 Week 11 所学的 ANTLR Visitor，Starter 默认的 `StringBuilder` 可删除或更换。

## 项目结构

```text
Splc.g4                         可修改 Parser Grammar
SplLexer.g4                     固定 Lexer
src/main/java/impl/IRGenerator.java   学生代码生成器
src/main/java/Main.java         固定编译与评测入口
runtime/runtime.c               固定 read/write/pow 与 C main 调用 SPL main0
examples/                       5 个最小 SPL + stdin + stdout 例子
testcases/project5/in/          3 个公开正式测试输入文件
testcases/project5/out/         3 个公开预期输出文件
TESTDATA_FORMAT.md              输入/输出格式说明
docs/Project5_LLVM_IR_Generation_v9.pdf  课程 Project 说明
Makefile                        本地构建、测试、提交
```

## 构建与自测

要求 JDK 17+、ANTLR4 complete JAR、LLVM 19（`opt-19`、`clang-19`）、Python 3.10+ 与 Make。

```bash
make help
make build ANTLR_JAR=/usr/local/share/antlr/antlr.jar
make test ANTLR_JAR=/usr/local/share/antlr/antlr.jar
make test-public ANTLR_JAR=/usr/local/share/antlr/antlr.jar
make check-data
make ir SOURCE=examples/hello.spl
make run SOURCE=examples/hello.spl STDIN=examples/hello.stdin
make handin
make clean
```

`make test` 运行 5 个简单例子及 30 组公开正式测试。Starter 的 `IRGenerator` 还没有实现，因此初始 `make test` **预期不会通过行为测试**；`make build` 仍应能编译。

本地调试与正式评测中，生成程序每组输入的执行时限统一为 5 秒。

## 公开测试 `.in/.out` 格式

每个 `.in` 包含一份 SPL 源码和 N 组独立 stdin；第一行 `CASES N` 明确给出输入组数，后续用独占整行的特殊标记分隔：

```text
CASES 2
=== SOURCE ===
int main0() {
    write(read() * 2);
    return 0;
}
=== INPUT 1 ===
10
=== INPUT 2 ===
20
=== END ===
```

对应 `.out` 为直接可读的标准输出：

```text
CASES 2
=== OUTPUT 1 ===
20
=== OUTPUT 2 ===
40
=== END ===
```

详细约束见 [TESTDATA_FORMAT.md](TESTDATA_FORMAT.md)。不需要统计字节数、进行 Base64 或十六进制编码。`make test` 会自动解析和比较，学生不需要在 `IRGenerator` 中处理测试协议。

## 提交和评分

```bash
make handin
```

生成 `submission.zip`，仅包含 `Splc.g4` 及 `src/main/java/impl/` 下的 Java 文件。**上传该 ZIP，不要上传整个 Starter ZIP。**

正式评分共 10 份 SPL 程序，每份程序 10 组输入，每组 1 分，共 100 分。公开 3 份（30 分），隐藏 7 份（70 分）。同一份 SPL 仅编译一次，然后使用各组 stdin 独立运行。生成或 LLVM 编译失败时，该 SPL 的 10 组均不能得分；其他程序仍独立评测。

Hydro 继续使用已部署的 `cs323project` 自定义语言接口：`submission.zip` → `oj-compile.sh submission.zip /usr/local/share/antlr/antlr.jar` → `project.jar` → `Main`。不要修改公共语言配置。
