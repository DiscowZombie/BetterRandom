#!/bin/sh
echo '<!DOCTYPE html><html><head /><body style="font-family: sans-serif;">' > betterrandom/src/main/javadoc/overview.html
ruby ./render-readme-for-javadoc.rb >> betterrandom/src/main/javadoc/overview.html
echo '</body></html>' >> betterrandom/src/main/javadoc/overview.html
cd betterrandom
mvn javadoc:javadoc
cd ..
rm -rf docs
mv betterrandom/target/site/apidocs docs
git add docs
git commit -m "🤖 Update Javadocs for GitHub Pages"
git push
