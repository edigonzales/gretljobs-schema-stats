///usr/bin/env jbang "$0" "$@" ; exit $?
//DEPS com.github.jsqlparser:jsqlparser:5.3
//DEPS org.eclipse.jgit:org.eclipse.jgit:6.9.0.202403050737-r

import static java.lang.System.*;

import net.sf.jsqlparser.parser.CCJSqlParserUtil;
import net.sf.jsqlparser.statement.Statement;
import net.sf.jsqlparser.schema.Table;
import net.sf.jsqlparser.util.TablesNamesFinder;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class SchemaAnalyzer {

    public static void main(String... args) throws InvalidRemoteException, TransportException, GitAPIException, IOException {
        Path repoPath = Paths.get("gretljobs");

        // Clone if not exists
        if (!Files.exists(repoPath)) {
            System.out.println("Cloning repository...");
            Git.cloneRepository()
                .setURI("https://github.com/sogis/gretljobs.git")
                .setDirectory(repoPath.toFile())
                .call();
        }

        // Map of root subfolder -> Set of schemas
        Map<Path, Set<String>> schemaMap = new HashMap<>();

        try (Stream<Path> paths = Files.walk(repoPath)) {
            List<Path> sqlFiles = paths
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".sql"))
                .collect(Collectors.toList());

            for (Path sqlFile : sqlFiles) {
                Path relative = repoPath.relativize(sqlFile);
                out.println(relative);
                if (relative.getNameCount() < 2) continue; // Skip files not in a subfolder
                Path rootSubfolder = relative.subpath(0, 1);
                //out.println(rootSubfolder);

                Set<String> schemas = schemaMap.computeIfAbsent(rootSubfolder, k -> new HashSet<>());

                String sql = Files.readString(sqlFile);
                String[] statements = sql.split(";");
                for (String stmt : statements) {
                    if (stmt.isBlank()) continue;
                    String sanitizedStmt = sanitizeStatement(stmt.trim());
                    try {
                        //out.println("**"+sanitizedStmt+"**");
                        if (relative.toString().contains("agi_kartenkatalog_pub")) {
                            out.println(sanitizedStmt);
                        }

                        if (sanitizedStmt.toUpperCase().contains("CREATE EXTENSION")) continue; 
                        if (sanitizedStmt.toUpperCase().startsWith("SET")) continue;
                        if (sanitizedStmt.toUpperCase().contains("LOAD ")) continue;
                        if (sanitizedStmt.toUpperCase().contains("ATTACH ")) continue;
                        if (sanitizedStmt.toUpperCase().contains("DETACH ")) continue;
                        if (sanitizedStmt.toUpperCase().contains("COPY ")) continue;

                        Statement statement = CCJSqlParserUtil.parse(sanitizedStmt, parser -> parser
                            .withAllowComplexParsing(true)
                            .withTimeOut(6000));

                        
                            Set<String> tableNames = TablesNamesFinder.findTables(statement.toString());
                        for (String tableName : tableNames) {
                            if (tableName.contains(".")) {
                                String schema = tableName.substring(0, tableName.indexOf("."));
                                schemas.add(schema);
                                // Add the extracted schema to the set, normalizing to lowercase for distinctness.
                                // A simple heuristic to exclude common database aliases if they appear as schemas.
                                // if (!schema.equalsIgnoreCase("DUAL") && !schema.equalsIgnoreCase("SYSDUMMY1")
                                //         && !schema.equalsIgnoreCase("information_schema")) { // Exclude common system schemas
                                //     distinctSchemas.add(schema.toLowerCase());
                                // }
                            }
            
                        }


                        // TablesNamesFinder finder = new TablesNamesFinder();
                        // finder.getTableList(statement).forEach(tableName -> {
                        //     out.println(tableName);
                        // });

                    } catch (Exception e) {
                        System.err.println("Error parsing: " + sanitizedStmt + " (" + e.getMessage() + ")");
                    }
                }   
            }
        }

        System.out.println("\nSchema counts per root subfolder:\n");
        schemaMap.forEach((folder, schemas) ->
            System.out.printf("%s,%d unique schemas\n", folder, schemas.size()));
    }

    private static String sanitizeStatement(String stmt) {
        stmt = replacePlaceholders(stmt);
        stmt = addIndexName(stmt);
        return stmt;
    }

    private static String addIndexName(String stmt) {
        String stmt2 = stmt.replaceAll("\\s+", " ").trim();
        //stmt = stmt.trim();
        if (stmt2.toUpperCase().startsWith("CREATE INDEX ON")) {
            // out.println("******************");
            // out.println(stmt2);
            return stmt2.replaceFirst("(?i)CREATE INDEX ON", "CREATE INDEX idx_temp ON");
        }
        return stmt;
    }

    private static String replacePlaceholders(String input) {
        Pattern pattern = Pattern.compile("\\$\\{([a-zA-Z0-9_]+)\\}");
        Matcher matcher = pattern.matcher(input);

        Map<String, String> replacements = new HashMap<>();
        StringBuffer result = new StringBuffer();
        int counter = 1;

        while (matcher.find()) {
            String key = matcher.group(1);

            // If this key hasn't been replaced before, generate a new replacement
            replacements.putIfAbsent(key, "xxx_" + counter++);
            String replacement = replacements.get(key);

            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }

        matcher.appendTail(result);
        return result.toString();
    }
}
