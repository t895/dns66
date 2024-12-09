DNS-Based Host Blocking for Android
===================================
Based on DNS66, this projects aims to continue the goals of the original
app with modern Android development practices.

This is a DNS-based host blocker for Android. In the default configuration,
several widely-respected host files are used to block ads, malware, and other
weird stuff.

Installing
----------
Download the APK here
https://github.com/t895/DNSNet/releases

Using it
---------
There's also no validation of input, so DNS servers that are not valid IPv4
addresses are not rejected, neither are URLs for DNS server entries (we intend
to support URLs in the future, so you can point the app to a remote list of
servers).

How it works
------------
The app establishes a VPN service, with routes for all DNS servers diverted to
it. The VPN service then intercepts the packages for the servers and forwards
any DNS queries that are not blacklisted.

Custom upstream DNS can be configured. If the feature is turned off, the
current connection's DNS servers are used.

Privacy Guarantee
-----------------
Privacy is the most important aspect of DNSNet. Currently, DNSNet is strictly
data reducing: Running it can only reduce the amount of data leaving your
device, not increase it (except for fetching hosts files, obviously), as for
each request, we will either allow it to leave your device or not - we will
not send other requests or add other information to the request.

Contributing
------------
See [CONTRIBUTING.md](CONTRIBUTING.md)

License
-------
This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the [License](COPYING), or
(at your option) any later version.

Code of Conduct
---------------
Please note that this project is released with a Contributor Code of
Conduct. By participating in this project you agree to abide by its terms.

Authors
-------
Charles Lombardo <clombardo169@gmail.com>

The app is based on the UI and services created by Julian Andres Klode <jak@jak-linux.org>

Parts are derived from https://github.com/dbrodie/AdBuster by Daniel Brodie.
