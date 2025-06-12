///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.jsqlparser:jsqlparser:5.3
//DEPS org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r

import static java.lang.System.*;

import net.sf.jsqlparser.JSQLParserException;
import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.util.TablesNamesFinder;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ParseFile {

    public static void main(String... args) throws IOException, JSQLParserException {

        String fileName = args[0];
        String sql = Files.readString(Paths.get(fileName));
        Statement statement = CCJSqlParserUtil.parse(sql);

        


    }
}
