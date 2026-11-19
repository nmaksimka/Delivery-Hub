{{- define "dh.env" -}}
{{- range $key, $value := .c.env }}
- name: {{ $key }}
  value: {{ $value | quote }}
{{- end }}
{{- range $key, $secretKey := .c.secretEnv }}
- name: {{ $key }}
  valueFrom:
    secretKeyRef:
      name: {{ include "dh.secretName" $.root }}
      key: {{ $secretKey }}
{{- end }}
{{- end }}

{{- define "dh.waitFor" -}}
{{- range .c.waitFor }}
- name: wait-{{ . | replace ":" "-" }}
  image: {{ $.root.Values.waitImage }}
  command:
    - sh
    - -c
    - until nc -z {{ . | replace ":" " " }}; do sleep 2; done
{{- end }}
{{- end }}

{{- define "dh.ports" -}}
{{- range .c.ports }}
- name: {{ .name }}
  containerPort: {{ .port }}
{{- end }}
{{- end }}
