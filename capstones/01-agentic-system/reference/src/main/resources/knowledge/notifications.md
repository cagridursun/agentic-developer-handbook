# Notifications on Helio

> Helio is a fictional developer platform invented for this handbook. Nothing
> in this document describes a real company or product.

The notifications module delivers email and webhooks through the worker pool.
Healthy delivery completes within 30 seconds end to end; the p99 delivery
latency dashboard is the primary signal.

Slow delivery shows first in p99 latency. Degradation indicators, in order of
usefulness:

- p99 delivery latency above 120 seconds
- retry rate above 5 percent
- worker pool queue depth growing for more than 10 minutes

To investigate, check the current service status first, then look at what
changed recently. Reading status and deployment data is always safe; do not
restart workers as a diagnostic step.
