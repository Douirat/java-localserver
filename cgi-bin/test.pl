#!/usr/bin/env perl
use strict;
use warnings;

print "Content-Type: text/html\n\n";

print "<!DOCTYPE html>\n";
print "<html>\n";
print "<head><title>Perl CGI Test</title></head>\n";
print "<body>\n";
print "<h1>Perl CGI Script Test</h1>\n";
print "<h2>Environment Variables:</h2>\n";
print "<ul>\n";

foreach my $key (sort keys %ENV) {
    if ($key =~ /^HTTP_/ || $key =~ /^(REQUEST_METHOD|PATH_INFO|QUERY_STRING|CONTENT_TYPE|CONTENT_LENGTH|SERVER_SOFTWARE|SERVER_NAME|GATEWAY_INTERFACE|REMOTE_ADDR|SCRIPT_NAME|SCRIPT_FILENAME)$/) {
        print "<li><strong>$key</strong>: $ENV{$key}</li>\n";
    }
}

print "</ul>\n";
print "<h2>Request Method:</h2>\n";
print "<p>$ENV{'REQUEST_METHOD'}</p>\n";
print "<h2>PATH_INFO:</h2>\n";
print "<p>$ENV{'PATH_INFO'}</p>\n";
print "<h2>QUERY_STRING:</h2>\n";
print "<p>$ENV{'QUERY_STRING'}</p>\n";
print "</body>\n";
print "</html>\n";
