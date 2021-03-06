Resource Query Parser
===================

Lucene query parser for resource such as patent.

## Features:

###1. Convert resource query to lucene query.
>"ti=car" => "ti:car"

###2. Ignore case query.
>"TI=car and co=us" => "+ti:car +co:us"

###3. Field check.
>If you defined "fields", that checks for the existence of field in query.It will throw MissingFieldException when field not exit.

###4. Field alias.
>If you defined "fieldMap", for example, "名称"->"ti", "名称:car" the same as "ti:car".

###5. Return all fields query text, usually used to highlight or analysis.
###6. Support reference query.
>For example, "#1"->"ti=reference", "ab=car or #1" the same as "ab=car or ti=reference".

###7. Return reference query real query.
>For example,"#1"->"ti=a","#2"->"ab=b", if you search "#1 and #2", can get "ti=a and ab=b".
