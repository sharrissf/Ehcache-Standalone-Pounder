#!/usr/bin/perl -w

use strict;

my %templates = (
		 "500MB-BigMemory" => [.5, 1],
		 "500MB-on-heap" => [.5, 0],
		 "1GB-BigMemory" => [1, 1],
		 "1GB-on-heap" => [1, 0],
		 "10GB-BigMemory" => [10, 1],
		 "10GB-on-heap" => [10, 0],
		 "20GB-BigMemory" => [20, 1],
		 "20GB-on-heap" => [20, 0],
		 "30GB-BigMemory" => [20, 1],
		 "30GB-on-heap" => [20, 0],
		 "100GB-BigMemory" => [100, 1],
		 "100GB-on-heap" => [100, 0],
		 "200GB-BigMemory" => [200, 1],
		 "200GB-on-heap" => [200, 0],
		 "300GB-BigMemory" => [300, 1],
		 "300GB-on-heap" => [300, 0]
		);

foreach my $template_name (sort keys %templates) {
  my $args = $templates{$template_name};
  my $arglist = join " ", @$args;
  my $cmd = "perl ./bake-template.pl $template_name $arglist";
  print "Executing $cmd\n";
  `$cmd`;
}
