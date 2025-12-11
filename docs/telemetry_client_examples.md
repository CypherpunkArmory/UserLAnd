# Telemetry client examples in other programming languages

These snippets demonstrate how to consume the JSON telemetry that `raf_kerneld` exposes (e.g., `GET /status`) from a few common languages.

> Make sure the daemon is already running locally (see the instructions in the root `README`) before trying the examples below.

## Python 3

```python
import requests

response = requests.get("http://127.0.0.1:8027/status", timeout=5)
response.raise_for_status()
status = response.json()

print("CPU count:", len(status["cpus"]))
print("MemFree:", status["meminfo"].get("MemFree"))
```

## Node.js (using `node` 18+ or the `node-fetch` polyfill)

```javascript
import fetch from "node-fetch"; // If running under Node 18+ with global fetch, remove this import.

const res = await fetch("http://127.0.0.1:8027/status");
if (!res.ok) {
  throw new Error(`Request failed with status ${res.status}`);
}
const status = await res.json();

console.log("CPU count:", status.cpus.length);
console.log("MemFree:", status.meminfo?.MemFree);
```

## Go

```go
package main

import (
    "encoding/json"
    "fmt"
    "net/http"
)

type Status struct {
    CPUs []interface{}       `json:"cpus"`
    MemInfo map[string]int64 `json:"meminfo"`
}

func main() {
    resp, err := http.Get("http://127.0.0.1:8027/status")
    if err != nil {
        panic(err)
    }
    defer resp.Body.Close()

    if resp.StatusCode != http.StatusOK {
        panic(fmt.Sprintf("unexpected status: %d", resp.StatusCode))
    }

    var status Status
    if err := json.NewDecoder(resp.Body).Decode(&status); err != nil {
        panic(err)
    }

    fmt.Println("CPU count:", len(status.CPUs))
    fmt.Println("MemFree:", status.MemInfo["MemFree"])
}
```

Each example simply calls the `/status` endpoint, parses the JSON payload, and prints a couple of fields to confirm that the daemon is reachable.
