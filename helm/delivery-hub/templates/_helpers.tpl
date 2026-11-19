{{- define "dh.selector" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .root.Release.Name }}
{{- end }}

{{- define "dh.labels" -}}
{{ include "dh.selector" . }}
app.kubernetes.io/part-of: delivery-hub
app.kubernetes.io/managed-by: {{ .root.Release.Service }}
helm.sh/chart: {{ printf "%s-%s" .root.Chart.Name .root.Chart.Version }}
{{- end }}

{{- define "dh.configName" -}}
{{ .Release.Name }}-config
{{- end }}

{{- define "dh.secretName" -}}
{{ .Release.Name }}-secrets
{{- end }}
