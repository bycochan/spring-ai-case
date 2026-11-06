@echo off
rem 后台启动 spring-ai-case（智谱AI GLM），API Key 来自项目根目录 .env
cd /d d:\example\spring-ai-case
"D:\tools\maven\apache-maven-3.9.16\bin\mvn.cmd" -B spring-boot:run ^
  -Dskip.npm=true ^
  -Dskip.installnodenpm=true ^
  -Dspring-boot.run.jvmArguments=-Dzhipuai-api-key=e5b17fb7659147d293f845fe7f142f9a.fNGMmW9vYaaKnLi2 ^
  > boot.log 2>&1
