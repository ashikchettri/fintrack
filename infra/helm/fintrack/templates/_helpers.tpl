{{/* Common labels applied to every object. */}}
{{- define "fintrack.labels" -}}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
app.kubernetes.io/part-of: fintrack
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
{{- end -}}

{{/*
Fully-qualified image ref for a service.
Usage: include "fintrack.image" (dict "name" $name "root" $)
*/}}
{{- define "fintrack.image" -}}
{{- $img := .root.Values.image -}}
{{- if $img.registry }}{{ $img.registry }}/{{ end }}{{ $img.repository }}/{{ .name }}:{{ $img.tag }}
{{- end -}}

{{/* The Secret name to read (existing override, else the chart's own). */}}
{{- define "fintrack.secretName" -}}
{{- .Values.secrets.existingSecret | default (printf "%s-secrets" .Release.Name) -}}
{{- end -}}
