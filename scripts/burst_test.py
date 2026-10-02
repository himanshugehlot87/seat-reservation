import concurrent.futures
import requests

URL = "http://localhost:8080/shows/3/reserve"

TOTAL_REQUESTS = 100

def reserve_seat(index):
    headers = {
        "Authorization": "Bearer user-" + str(index),
        "Idempotency-Key": "burst-" + str(index),
        "Content-Type": "application/json"
    }

    seats = ["A1", "A2", "A3", "A4", "A5"]
    body = {"seats": [seats[index % len(seats)]]}

    response = requests.post(
        URL,
        headers=headers,
        json=body
    )

    return response.status_code


with concurrent.futures.ThreadPoolExecutor(
        max_workers=TOTAL_REQUESTS) as executor:

    results = list(
        executor.map(
            reserve_seat,
            range(TOTAL_REQUESTS)
        )
    )


success = results.count(201)
conflict = results.count(409)
other = len(results) - success - conflict

print("Total requests :", TOTAL_REQUESTS)
print("201 Created    :", success)
print("409 Conflict   :", conflict)
print("Other responses:", other)