#!/usr/bin/env python3
"""Prints the failing test cases (Class.method) found in a surefire reports directory, sorted and unique."""
import glob
import sys
import xml.etree.ElementTree as ET

names = set()
for path in glob.glob(sys.argv[1] + "/TEST-*.xml"):
    for case in ET.parse(path).getroot().iter("testcase"):
        if case.find("failure") is not None or case.find("error") is not None:
            cls = case.get("classname").split(".")[-1]
            method = case.get("name").split("(")[0].split("[")[0].strip()
            names.add(cls + "." + method)
print(",".join(sorted(names)[:3]) if names else "")
