// Application code lives in app/, conventions in build-logic/. The root only holds repo-wide checks.

val checkFileTokens by tasks.registering(Exec::class) {
    description = "Fails when a file agents write is over 5,000 tokens (bytes/4), with advice on how to split it."
    commandLine("python3", "scripts/check_file_tokens.py", "--all")
}

tasks.register("check") {
    dependsOn(checkFileTokens)
}
