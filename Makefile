
.PHONY: test


install: target

target:
	./build.sh

test:
	lein midje
